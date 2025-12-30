package service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import implementation.compare.ChangesService;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import io.reactivex.rxjava3.core.Observable;
import model.Debounce;
import model.MyModel;
import model.MyModelBase;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import state.State;
import implementation.scope.MyScope;
import system.Defs;
import toolwindow.elements.VcsTree;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class ViewService implements Disposable {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(ViewService.class);

    public static final String PLUS_TAB_LABEL = "+";
    public static final int DEBOUNCE_MS = 50;
    public List<MyModel> collection = new ArrayList<>();
    public Integer currentTabIndex = 0;
    private final Project project;
    private boolean isProcessingTabRename = false;
    private boolean isProcessingTabReorder = false;
    private ToolWindowServiceInterface toolWindowService;
    private TargetBranchService targetBranchService;
    private ChangesService changesService;
    private State state;
    private MyLineStatusTrackerImpl myLineStatusTrackerImpl;
    private Debounce debouncer;
    private MyScope myScope;
    private GitService gitService;
    private StatusBarService statusBarService;
    private boolean vcsReady = false;
    private boolean toolWindowReady = false;
    private MyModel myHeadModel;
    private boolean isInit;
    private int lastTabIndex;
    private Integer savedTabIndex;
    private final AtomicBoolean tabInitializationInProgress = new AtomicBoolean(false);

    public ViewService(Project project) {
        this.project = project;
        EventQueue.invokeLater(this::initDependencies);
    }

    private final @NotNull ExecutorService changesExecutor =
            SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ViewServiceChanges");
    private final AtomicLong applyGeneration = new AtomicLong(0);

    @Override
    public void dispose() {
    }

    public void initDependencies() {
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.statusBarService = project.getService(StatusBarService.class);
        this.gitService = project.getService(GitService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = project.getService(State.class);
        this.myLineStatusTrackerImpl = new MyLineStatusTrackerImpl(project, this);
        this.myScope = new MyScope(project);
        this.debouncer = new Debounce();
    }

    private void doUpdateDebounced(Collection<Change> changes) {
        debouncer.debounce(Void.class, () -> onUpdate(changes), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Refreshes file status colors for files in the current scope and previous scope.
     * Call this when the active scope changes to update colors based on new scope.
     *
     * This method refreshes files from BOTH the new active model and the previous active model to ensure:
     * - Files in the new scope get the new colors
     * - Files that were in the old scope but not in the new scope revert to default colors
     *
     */
    public void refreshFileColors() {
        if (project.isDisposed()) {
            return;
        }

        // Execute file status refresh on background thread to avoid EDT slow operations
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) {
                return;
            }
            
            // Then update on EDT with bulk refresh
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                
                com.intellij.openapi.vcs.FileStatusManager fileStatusManager =
                    com.intellij.openapi.vcs.FileStatusManager.getInstance(project);
                
                // Bulk update all file statuses
                fileStatusManager.fileStatusesChanged();
            });
        });
    }

    public void load() {
        // Load models from state
        List<MyModel> collection = new ArrayList<>();
        List<MyModelBase> load = this.state.getModelData();
        if (load == null) {
            return;
        }

        load.forEach(myModelBase -> {
            MyModel myModel = new MyModel();

            // Load the target branch map
            TargetBranchMap targetBranchMap = myModelBase.getTargetBranchMap();
            if (targetBranchMap == null) {
                return;
            }
            myModel.setTargetBranchMap(targetBranchMap);

            // Load the custom tab name
            String customTabName = myModelBase.getCustomTabName();
            if (customTabName != null && !customTabName.isEmpty()) {
                myModel.setCustomTabName(customTabName);
            }

            collection.add(myModel);
        });

        setCollection(collection);

        // Remember the saved active tab index
        savedTabIndex = this.state.getCurrentTabIndex();
    }

    public void save() {
        if (collection == null) {
            return;
        }

        List<MyModelBase> modelData = new ArrayList<>();
        collection.forEach(myModel -> {
            MyModelBase myModelBase = new MyModelBase();

            // Save the target branch map
            TargetBranchMap targetBranchMap = myModel.getTargetBranchMap();
            if (targetBranchMap == null) {
                LOG.debug("Skipping model with null targetBranchMap during save");
                return;
            }
            myModelBase.setTargetBranchMap(targetBranchMap);

            // Save the custom tab name
            String customTabName = myModel.getCustomTabName();
            if (customTabName != null && !customTabName.isEmpty()) {
                myModelBase.setCustomTabName(customTabName);
            }

            modelData.add(myModelBase);
        });

        this.state.setModelData(modelData);

        // Save the current active tab index
        this.state.setCurrentTabIndex(this.currentTabIndex);
    }

    public void eventVcsReady() {
        this.vcsReady = true;
        init();
    }

    public void eventToolWindowReady() {
        this.toolWindowReady = true;
        init();
    }

    public void init() {
        if (!vcsReady || !toolWindowReady || isInit) {
            return;
        }

        isInit = true;
        EventQueue.invokeLater(this::initLater);
    }

    public void initLater() {
        // First, ensure all tabs are removed to prevent duplicates
        toolWindowService.removeAllTabs();

        // Then load models and subscribe to their observables
        load();

        List<MyModel> modelCollection = getCollection();
        modelCollection.forEach(this::subscribeToObservable);

        // Initialize tabs in a synchronized manner
        initTabsSequentially();

        // Set the active model
        setActiveModel();

        if (savedTabIndex != null) {
            this.currentTabIndex = savedTabIndex;
            runAfterCurrentChangeCollection(() -> {
                if (toolWindowService != null) {
                    toolWindowService.selectTabByIndex(savedTabIndex);
                    setActiveModel();
                }
            });
        }
    }

    public void initTabsSequentially() {
        // If tab initialization is already in progress, don't start another one
        if (!tabInitializationInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            // Step 1: First remove all tabs to start clean
            toolWindowService.removeAllTabs();

            // Step 2: Init head tab first
            initHeadTab();

            // Step 3: Process each model sequentially but use the synchronous method for reliability
            List<MyModel> modelCollection = this.getCollection();
            if (!modelCollection.isEmpty()) {
                for (MyModel model : modelCollection) {
                    String tabName = model.getDisplayName();
                    toolWindowService.addTab(model, tabName, true);

                    // Set up tooltip for each tab that has a custom name
                    if (model.getCustomTabName() != null && !model.getCustomTabName().isEmpty()) {
                        toolWindowService.setupTabTooltip(model);
                    }
                }
            }

            // Step 4: Add the listener after all tabs are initialized
            toolWindowService.addListener();

            // Step 5: Finally add the plus tab (which should not be closeable)
            addPlusTab();

        } finally {
            tabInitializationInProgress.set(false);
        }
    }

    private void initHeadTab() {
        this.myHeadModel = new MyModel(true);
        toolWindowService.addTab(myHeadModel, GitService.BRANCH_HEAD, false);
        subscribeToObservable(myHeadModel);
    }

    public void addRevisionTab(String revision) {
        if (GitService.BRANCH_HEAD.equals(revision)) {
            if (myHeadModel != null) {
                toolWindowService.selectTabByIndex(0);
                setTabIndex(0);
                setActiveModel();
            }
            return;
        }

        MyModel myModel = addTabAndModel(revision);

        gitService.getRepositoriesAsync(repositories -> {
            repositories.forEach(repo -> {
                myModel.addTargetBranch(repo, revision);
            });

            // Set up tooltip after target branches are added
            if (myModel.getCustomTabName() != null && !myModel.getCustomTabName().isEmpty()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    toolWindowService.setupTabTooltip(myModel);
                });
            }
        });
    }

    private void addPlusTab() {
        toolWindowService.addTab(new MyModel(), PLUS_TAB_LABEL, false);
    }

    public void plusTabClicked() {
        String tabName = "New*";
        addTabAndModel(tabName);
    }

    public MyModel addTabAndModel(String tabName) {
        MyModel myModel = addModel();

        // Get ContentManager to find current + tab position
        ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();
        int contentCountBefore = contentManager.getContentCount();

        // Find the + tab (should be at the last position)
        int plusTabIndex = -1;
        for (int i = 0; i < contentCountBefore; i++) {
            Content content = contentManager.getContent(i);
            if (content != null && PLUS_TAB_LABEL.equals(content.getTabName())) {
                plusTabIndex = i;
                break;
            }
        }

        // Remove the old + tab first
        if (plusTabIndex >= 0) {
            toolWindowService.removeTab(plusTabIndex);
        }

        // Make sure to add the tab with closeable set to true
        toolWindowService.addTab(myModel, tabName, true);

        // Set up tooltip for the new tab
        if (myModel.getCustomTabName() != null && !myModel.getCustomTabName().isEmpty()) {
            toolWindowService.setupTabTooltip(myModel);
        }

        // Add plus tab after the new tab
        addPlusTab();

        // Set the current tab index and active model
        setTabIndex(collection.size());
        setActiveModel();

        // Select the new tab (should be second to last, before the + tab)
        toolWindowService.selectNewTab();

        return myModel;
    }

    public void toggleActionInvoked() {
        int toggleToIndex = (currentTabIndex == 0) ? lastTabIndex : 0;
        setTabIndex(toggleToIndex);
        setActiveModel();
        toolWindowService.selectTabByIndex(toggleToIndex);
    }

    private void subscribeToObservable(MyModel model) {
        Observable<MyModel.field> observable = model.getObservable();
        observable.subscribe(field -> {
            switch (field) {
                case targetBranch -> {
                    getTargetBranchDisplayAsync(model, tabName -> {
                        toolWindowService.changeTabName(tabName);
                    });
                    // TODO: collectChanges: target branch selected (Git Scope selected)
                    incrementUpdate();
                    collectChanges(model, true);
                    save();
                }
                case changes -> {
                    if (model.isActive()) {
                        Collection<Change> changes = model.getChanges();
                        doUpdateDebounced(changes);
                        // Note: We don't manually refresh file colors here because:
                        // 1. It interferes with gutter change bars being set up
                        // 2. FileStatusProvider is automatically queried by IntelliJ when needed
                        // 3. Manual refresh causes race conditions with the line status tracker
                    }
                }
                // TODO: collectChanges: tab switched
                case active -> {
                    incrementUpdate(); // Increment generation to cancel any stale updates for previous tab
                    collectChanges(model, true);
                    // Refresh file colors when switching tabs
                    refreshFileColors();
                }
                case tabName -> {
                    if (!isProcessingTabRename) {
                        String customName = model.getCustomTabName();
                        if (customName != null && !customName.isEmpty()) {
                            toolWindowService.changeTabName(customName);
                            toolWindowService.setupTabTooltip(model);
                        }
                    }
                }

            }

        }, (e -> {
        }));
    }

    private void getTargetBranchDisplayCurrent(Consumer<String> callback) {
        MyModel current = getCurrent();
        if (current == null) {
            return;
        }
        getTargetBranchDisplayAsync(current, callback);
    }

    // Update the getTargetBranchDisplayAsync method to use custom names
    private void getTargetBranchDisplayAsync(MyModel model, Consumer<String> callback) {
        // Special handling for HEAD model
        if (model.isHeadTab()) {
            callback.accept(GitService.BRANCH_HEAD);
            return;
        }

        // Check for custom name first
        String customName = model.getCustomTabName();
        if (customName != null && !customName.isEmpty()) {
            callback.accept(customName);
            return;
        }

        // Fall back to the standard branch-based name
        this.targetBranchService.getTargetBranchDisplayAsync(model.getTargetBranchMap(), callback);
    }


    @Deprecated
    private String getTargetBranchDisplayCurrent() {
        MyModel current = getCurrent();
        return getTargetBranchDisplay(current);
    }

    @Deprecated
    private String getTargetBranchDisplay(MyModel model) {
        return this.targetBranchService.getTargetBranchDisplay(model.getTargetBranchMap());
    }

    public void incrementUpdate() {
        long newGen = applyGeneration.incrementAndGet();
        LOG.debug("incrementUpdate() -> generation = " + newGen);
    }

    public CompletableFuture<Void> collectChanges(boolean checkFs) {
        return collectChanges(getCurrent(), checkFs);
    }

    /**
     * Ensures HEAD tab model has a targetBranchMap initialized with all repositories.
     * This is a lazy initialization that runs when HEAD tab is accessed, after repositories are loaded.
     * Uses async callback to avoid slow operations on EDT.
     */
    private void ensureHeadTabInitializedAsync(MyModel model, Runnable onComplete) {
        if (model.isHeadTab() && model.getTargetBranchMap() == null) {
            // Use async method to avoid slow operations on EDT
            gitService.getRepositoriesAsync(repositories -> {
                repositories.forEach(repo -> {
                    model.addTargetBranch(repo, null);
                });
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        } else {
            // Already initialized or not HEAD tab
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public CompletableFuture<Void> collectChanges(MyModel model, boolean checkFs) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (model == null) {
            done.complete(null);
            return done;
        }

        // Ensure HEAD tab is initialized before proceeding
        ensureHeadTabInitializedAsync(model, () -> {
            TargetBranchMap targetBranchMap = model.getTargetBranchMap();
            if (targetBranchMap == null) {
                done.complete(null);
                return;
            }

            collectChangesInternal(model, targetBranchMap, checkFs, done);
        });

        return done;
    }

    private void collectChangesInternal(MyModel model, TargetBranchMap targetBranchMap, boolean checkFs, CompletableFuture<Void> done) {

        // Make targetBranchMap effectively final for lambda
        final TargetBranchMap finalTargetBranchMap = targetBranchMap;
        final long gen = applyGeneration.get();
        LOG.debug("collectChanges() scheduled with generation = " + gen);

        // serialize collection behind a single-threaded executor
        changesExecutor.execute(() -> {
            changesService.collectChangesWithCallback(finalTargetBranchMap, result -> {
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        long currentGen = applyGeneration.get();
                        if (!project.isDisposed() && currentGen == gen) {
                            LOG.debug("Applying changes for generation " + gen);
                            model.setChanges(result.mergedChanges);
                            model.setLocalChanges(result.localChanges);
                        } else {
                            LOG.debug("Discarding changes for generation " + gen + " (current generation is " + currentGen + ")");
                        }
                    } finally {
                        done.complete(null);
                    }
                });
            }, checkFs);
        });
    }

    // helper to enqueue UI work strictly after the currently queued collections
    public void runAfterCurrentChangeCollection(Runnable uiTask) {
        changesExecutor.execute(() ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) uiTask.run();
                })
        );
    }


    public MyModel addModel() {
        MyModel model = new MyModel();
        this.collection.add(model);
        subscribeToObservable(model);
        return model;
    }

    public MyModel getCurrent() {
        // Check if toolWindowService is initialized
        if (toolWindowService == null) {
            return myHeadModel; // Safe fallback when not yet initialized
        }

        // Get the currently selected tab's model directly from ContentManager
        ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();
        Content selectedContent = contentManager.getSelectedContent();

        if (selectedContent == null) {
            return myHeadModel;
        }

        // Check if it's the HEAD tab
        if (selectedContent.getTabName().equals(GitService.BRANCH_HEAD)) {
            return myHeadModel;
        }

        // Check if it's the + tab
        if (selectedContent.getTabName().equals(PLUS_TAB_LABEL)) {
            return myHeadModel; // or handle differently
        }

        // Get the model for this content
        MyModel model = toolWindowService.getModelForContent(selectedContent);
        if (model != null) {
            return model;
        }

        // Fallback to HEAD
        return myHeadModel;
    }

    public List<MyModel> getCollection() {
        return collection;
    }

    public void setCollection(List<MyModel> collection) {
        this.collection = collection;
    }

    /**
     * Core private method to retrieve a cached HashMap from the current MyModel.
     * Handles initialization checks and returns the pre-built map.
     *
     * @param mapGetter Function to retrieve the cached map from MyModel
     * @return Map of file path to Change, or null if not initialized
     */
    private Map<String, Change> getChangesMapInternal(Function<MyModel, Map<String, Change>> mapGetter) {
        // Early return if ViewService is not fully initialized yet
        if (toolWindowService == null) {
            return null;
        }

        MyModel current = getCurrent();
        if (current == null) {
            return null;
        }

        return mapGetter.apply(current);
    }

    /**
     * Gets a HashMap of local changes indexed by file path for O(1) lookup.
     * Returns the cached map that was built when changes were set.
     *
     * @return Map of file path to Change, or null if not initialized
     */
    public Map<String, Change> getLocalChangesTowardsHeadMap() {
        return getChangesMapInternal(MyModel::getLocalChangesMap);
    }

    /**
     * Gets a HashMap of scope changes indexed by file path for O(1) lookup.
     * Returns the cached map that was built when changes were set.
     *
     * @return Map of file path to Change, or null if not initialized
     */
    public Map<String, Change> getCurrentScopeChangesMap() {
        return getChangesMapInternal(MyModel::getChangesMap);
    }

    public void removeTab(int tabIndex) {
        int modelIndex = getModelIndex(tabIndex);
        // Check if the index is valid before removing
        if (modelIndex >= 0 && modelIndex < collection.size()) {
            this.collection.remove(modelIndex);
            save();
        }
    }

    public void onTabReordered(int oldIndex, int newIndex) {
        // Note: The isProcessingTabReorder flag should already be set by the caller
        // before any UI changes are made, to prevent listener interference

        // After tabs are reordered in the UI, we need to rebuild the collection
        // to match the new tab order
        rebuildCollectionFromTabOrder();

        // Save the new order
        save();
    }

    /**
     * Rebuilds the collection based on the current tab order in ContentManager.
     * This ensures that collection[i] corresponds to tab[i+1] (accounting for HEAD at index 0).
     */
    private void rebuildCollectionFromTabOrder() {
        ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();
        List<MyModel> newCollection = new ArrayList<>();

        // Iterate through all tabs (skip HEAD at index 0, skip + at end)
        int contentCount = contentManager.getContentCount();
        for (int i = 1; i < contentCount; i++) {
            Content content = contentManager.getContent(i);
            if (content != null && !PLUS_TAB_LABEL.equals(content.getTabName())) {
                // Find the model for this content
                MyModel model = findModelForContent(content);
                if (model != null && !model.isHeadTab()) {
                    newCollection.add(model);
                } else {
                    LOG.warn("Model not found for tab at index " + i + ": " + content.getTabName());
                }
            }
        }

        // Replace the collection with the reordered one
        this.collection = newCollection;
    }

    /**
     * Finds the MyModel associated with a Content object by using the ToolWindowService.
     */
    private MyModel findModelForContent(Content content) {
        return toolWindowService.getModelForContent(content);
    }

    public boolean isProcessingTabReorder() {
        return isProcessingTabReorder;
    }

    public void setProcessingTabReorder(boolean processing) {
        this.isProcessingTabReorder = processing;
    }

    public void removeCurrentTab() {
        removeTab(currentTabIndex);
        // Also remove from the UI
        toolWindowService.removeTab(currentTabIndex);
    }

    public int getTabIndex() {
        return currentTabIndex;
    }

    public void setTabIndex(int index) {
        if (currentTabIndex > 0) {
            lastTabIndex = currentTabIndex;
        }
        currentTabIndex = index;
        // Don't increment generation here - it will be incremented when setActiveModel() triggers collectChanges()
        // This prevents a race condition where events between setTabIndex() and the observable firing get lost
        LOG.debug("setTabIndex(" + index + ")");
        save();
    }

    public void onTabRenamed(int tabIndex, String newName) {
        // Skip renaming for special tabs
        if (tabIndex == 0 || tabIndex >= collection.size() + 1) {
            return; // HEAD tab or Plus tab
        }

        if (isProcessingTabRename) {
            return;
        }

        try {
            isProcessingTabRename = true;

            // Update the model with custom name if needed
            int modelIndex = getModelIndex(tabIndex);
            if (modelIndex >= 0 && modelIndex < collection.size()) {
                MyModel model = collection.get(modelIndex);

                // Only update if the name has actually changed
                String currentCustomName = model.getCustomTabName();
                if (!newName.equals(currentCustomName)) {
                    model.setCustomTabName(newName);
                    save();
                }
            }
        } finally {
            isProcessingTabRename = false;
        }
    }

    // Add a method to get the display name considering custom names
    public String getDisplayNameForTab(MyModel model) {
        if (model.isHeadTab()) {
            return GitService.BRANCH_HEAD;
        }

        // Check for custom name first
        String customName = model.getCustomTabName();
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }

        // Fall back to the standard branch-based name
        return model.getDisplayName();
    }

    public int getCurrentModelIndex() {
        return getTabIndex() - 1;
    }

    public int getModelIndex(int index) {
        return index - 1;
    }

    public void setActiveModel() {
        this.collection.forEach(myModel -> {
            myModel.setActive(false);
        });
        MyModel current = this.getCurrent();
        if (current != null) {
            current.setActive(true);
        }
    }

    public void onUpdate(Collection<Change> changes) {
        if (changes == null) {
            return;
        }

        // Run UI updates on EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            updateStatusBarWidget();
            myLineStatusTrackerImpl.update(changes, null);
            myScope.update(changes);

            // Perform scroll restoration after all UI updates are complete
            // Use another invokeLater to ensure everything is fully rendered
            SwingUtilities.invokeLater(() -> {
                VcsTree vcsTree = toolWindowService.getVcsTree();
                if (vcsTree != null) {
                    vcsTree.performScrollRestoration();
                }
            });
        });
    }

    private void updateStatusBarWidget() {
        getTargetBranchDisplayCurrent(branchName -> {
            this.statusBarService.updateText(Defs.APPLICATION_NAME + ": " + branchName);
        });
    }
}