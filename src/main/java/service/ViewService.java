package service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ViewService implements Disposable {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(ViewService.class);

    public static final String PLUS_TAB_LABEL = "+";
    public static final int DEBOUNCE_MS = 50;
    public static final int HEAD_TAB_INIT_TIMEOUT = 5;  // sec
    public List<MyModel> collection = new ArrayList<>();
    public Integer currentTabIndex = 0;
    private final Project project;
    private boolean isProcessingTabRename = false;
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

        // Wait for repositories to be loaded before proceeding
        CountDownLatch latch = new CountDownLatch(1);

        gitService.getRepositoriesAsync(repositories -> {
            repositories.forEach(repo -> {
                myHeadModel.addTargetBranch(repo, null);
            });

            subscribeToObservable(myHeadModel);

            // TODO: collectChanges: head tab initialized
            incrementUpdate();
            collectChanges(myHeadModel, true);
            latch.countDown();
        });

        try {
            // Wait for the head tab initialization to complete with a timeout
            latch.await(HEAD_TAB_INIT_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Handle interruption if necessary
        }
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
        int currentIndexOfPlusTab = collection.size() + 1;
        MyModel myModel = addModel();

        // Make sure to add the tab with closeable set to true
        toolWindowService.addTab(myModel, tabName, true);

        // Set up tooltip for the new tab
        if (myModel.getCustomTabName() != null && !myModel.getCustomTabName().isEmpty()) {
            toolWindowService.setupTabTooltip(myModel);
        }

        // Add plus tab after the new tab
        addPlusTab();

        // Remove the old plus tab
        toolWindowService.removeTab(currentIndexOfPlusTab);

        // Set the current tab index and active model
        setTabIndex(collection.size());
        setActiveModel();

        // Select the new tab
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
                    }
                }
                // TODO: collectChanges: tab switched
                case active -> {
                    incrementUpdate(); // Increment generation to cancel any stale updates for previous tab
                    collectChanges(model, true);
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

    public CompletableFuture<Void> collectChanges(MyModel model, boolean checkFs) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (model == null) {
            LOG.warn("collectChanges() called but model is null");
            done.complete(null);
            return done;
        }
        TargetBranchMap targetBranchMap = model.getTargetBranchMap();
        if (targetBranchMap == null) {
            LOG.warn("collectChanges() called but targetBranchMap is null");
            done.complete(null);
            return done;
        }

        final long gen = applyGeneration.get();
        LOG.debug("collectChanges() scheduled with generation = " + gen);

        // serialize collection behind a single-threaded executor
        changesExecutor.execute(() -> {
            changesService.collectChangesWithCallback(targetBranchMap, changes -> {
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        long currentGen = applyGeneration.get();
                        if (!project.isDisposed() && currentGen == gen) {
                            LOG.debug("Applying changes for generation " + gen);
                            model.setChanges(changes);
                        } else {
                            LOG.debug("Discarding changes for generation " + gen + " (current generation is " + currentGen + ")");
                        }
                    } finally {
                        done.complete(null);
                    }
                });
            }, checkFs);
        });

        return done;
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
        int currentModelIndex = getCurrentModelIndex();
        List<MyModel> collection = this.getCollection();
        int size = collection.size();
        if (currentModelIndex >= size) {
            return myHeadModel;
        }
        if (getTabIndex() == 0) {
            return myHeadModel;
        }
        return collection.get(currentModelIndex);
    }

    public List<MyModel> getCollection() {
        return collection;
    }

    public void setCollection(List<MyModel> collection) {
        this.collection = collection;
    }

    public void removeTab(int tabIndex) {
        int modelIndex = getModelIndex(tabIndex);
        // Check if the index is valid before removing
        if (modelIndex >= 0 && modelIndex < collection.size()) {
            this.collection.remove(modelIndex);
            save();
        }
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