package service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import implementation.compare.ChangesService;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import io.reactivex.rxjava3.core.Observable;
import model.Debounce;
import model.MyModel;
import model.MyModelBase;
import model.TargetBranchMap;
import state.State;
import implementation.scope.MyScope;
import system.Defs;
import license.CheckLicense;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ViewService {

    public static final String PLUS_TAB_LABEL = "+";
    public static final int DEBOUNCE_MS = 50;
    public List<MyModel> collection = new ArrayList<>();
    public Integer currentTabIndex = 0;
    private Project project;
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
    private final AtomicBoolean tabInitializationInProgress = new AtomicBoolean(false);

    public ViewService(Project project) {
        this.project = project;
        EventQueue.invokeLater(this::initDependencies);
    }

    public void initDependencies() {
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.statusBarService = project.getService(StatusBarService.class);
        this.gitService = project.getService(GitService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = project.getService(State.class);
        this.myLineStatusTrackerImpl = new MyLineStatusTrackerImpl(project);
        this.myScope = new MyScope(project);
        this.debouncer = new Debounce();
    }

    private void doUpdateDebounced(Collection<Change> changes) {
        debouncer.debounce(Void.class, () -> onUpdate(changes), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void licenseCheck() {
        final Boolean isLicensed = CheckLicense.isLicensed();
        if (!Boolean.TRUE.equals(isLicensed)) {
            CheckLicense.requestLicense("Please register the plugin!");
        }
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
            TargetBranchMap targetBranchMap = myModelBase.targetBranchMap;
            if (targetBranchMap == null) {
                return;
            }
            myModel.setTargetBranchMap(targetBranchMap);
            collection.add(myModel);
        });

        setCollection(collection);
        
        // Restore the active tab index
        Integer savedTabIndex = this.state.getCurrentTabIndex();
        if (savedTabIndex != null) {
            this.currentTabIndex = savedTabIndex;
            // Schedule tab selection after UI is ready
            EventQueue.invokeLater(() -> {
                if (toolWindowService != null) {
                    toolWindowService.selectTabByIndex(savedTabIndex);
                    setActiveModel();
                }
            });
        }
    }

    public void save() {
        if (collection == null) {
            return;
        }

        List<MyModelBase> modelData = new ArrayList<>();
        collection.forEach(myModel -> {
            MyModelBase myModelBase = new MyModelBase();
            TargetBranchMap targetBranchMap = myModel.getTargetBranchMap();
            if (targetBranchMap == null) {
                return;
            }
            myModelBase.targetBranchMap = targetBranchMap;
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
            collectChanges(myHeadModel);
            latch.countDown();
        });

        try {
            // Wait for the head tab initialization to complete with a timeout
            latch.await(5, TimeUnit.SECONDS);
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
        String tabName = revision;
        MyModel myModel = addTabAndModel(tabName);

        gitService.getRepositoriesAsync(repositories -> {
            repositories.forEach(repo -> {
                myModel.addTargetBranch(repo, revision);
            });
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
                    collectChanges(model);
                    save();
                }
                case changes -> {
                    if (model.isActive()) {
                        Collection<Change> changes = model.getChanges();
                        doUpdateDebounced(changes);
                    }
                }
                case active -> {
                    collectChanges(model);
                }
            }

        }, (e -> {
        }));
    }

    private void getTargetBranchDisplayCurrent(Consumer<String> callback) {
        MyModel current = getCurrent();
        getTargetBranchDisplayAsync(current, callback);
    }

    private void getTargetBranchDisplayAsync(MyModel model, Consumer<String> callback) {
        // Special handling for HEAD model
        if (model.isHeadTab()) {
            callback.accept(GitService.BRANCH_HEAD);
            return;
        }
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

    public void collectChanges() {
        collectChanges(getCurrent());
    }

    public void collectChanges(MyModel model) {
        if (model == null) {
            return;
        }

        TargetBranchMap targetBranchMap = model.getTargetBranchMap();
        if (targetBranchMap == null) {
            return;
        }

        // Run potentially slow operations in the background
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            changesService.collectChangesWithCallback(targetBranchMap, changes -> {
                // Update UI on EDT
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) {
                        model.setChanges(changes);
                    }
                });
            });
        });
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
        save();
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
        this.getCurrent().setActive(true);
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
        });
    }

    private void updateStatusBarWidget() {
        getTargetBranchDisplayCurrent(branchName -> {
            this.statusBarService.updateText(Defs.APPLICATION_NAME + ": " + branchName);
        });
    }
}