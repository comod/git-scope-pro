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
import java.util.concurrent.TimeUnit;

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

        load();
        List<MyModel> modelCollection = getCollection();
        modelCollection.forEach(this::subscribeToObservable);
        initTabs();
        setActiveModel();
    }

    public void initTabs() {
        List<MyModel> collection = this.getCollection();
        int size = collection.size();
        initHeadTab();

        if (size > 0) {
            collection.forEach(model -> {
                String tabName = getTargetBranchDisplay(model);
                toolWindowService.addTab(model, tabName, true);
            });
        }
        toolWindowService.addListener();
        addPlusTab();
    }

    private void initHeadTab() {
        this.myHeadModel = new MyModel(true);
        toolWindowService.addTab(myHeadModel, GitService.BRANCH_HEAD, false);
        gitService.getRepositories().forEach(repo -> {
            myHeadModel.addTargetBranch(repo, null);
        });
        subscribeToObservable(myHeadModel);
        collectChanges(myHeadModel);
    }

    public void addRevisionTab(String revision) {
        String tabName = revision;
        MyModel myModel = addTabAndModel(tabName);
        gitService.getRepositories().forEach(repo -> {
            myModel.addTargetBranch(repo, revision);
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
        toolWindowService.addTab(myModel, tabName, true);
        addPlusTab();
        toolWindowService.removeTab(currentIndexOfPlusTab);
        setTabIndex(collection.size());
        setActiveModel();
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
                    toolWindowService.changeTabName(getTargetBranchDisplay(model));
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

    private String getTargetBranchDisplayCurrent() {
        MyModel current = getCurrent();
        return getTargetBranchDisplay(current);
    }

    private String getTargetBranchDisplay(MyModel model) {
        return this.targetBranchService.
                getTargetBranchDisplay(model.getTargetBranchMap());
    }

    public void collectChanges() {
        collectChanges(getCurrent());
    }

    public void collectChanges(MyModel model) {
        if (model == null) {
            return;
        }
        TargetBranchMap targetBranchMap = model.getTargetBranchMap();

        // Run potentially slow operations in background
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
        this.collection.remove(getModelIndex(tabIndex));
        save();
    }

    public int getTabIndex() {
        return currentTabIndex;
    }

    public void setTabIndex(int index) {
        // @todo need ID instead of index
        if (currentTabIndex > 0) {
            lastTabIndex = currentTabIndex;
        }
        currentTabIndex = index;
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
        this.statusBarService.updateText(Defs.APPLICATION_NAME + ": " + getTargetBranchDisplayCurrent());
    }
}