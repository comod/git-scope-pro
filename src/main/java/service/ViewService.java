package service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import implementation.compare.ChangesService;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import io.reactivex.rxjava3.core.Observable;
import license.CheckLicense;
import model.Debounce;
import model.MyModel;
import model.MyModelBase;
import model.TargetBranchMap;
import state.State;
import implementation.scope.MyScope;
import system.Defs;
import license.CheckLicense;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ViewService {

    public static final String PLUS_TAB_LABEL = "+";
    public static final int DEBOUNCE_MS = 50;
    //    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public List<MyModel> collection = new ArrayList<>();
    public Integer currentTabIndex = 0;
    private Project project;
    private ToolWindowServiceInterface toolWindowService;
    private TargetBranchService targetBranchService;
    private ChangesService changesService;
    private State state;
    //    private final MessageBusConnection messageBusConnection;
//        private final MessageBusConnection messageBusConnection;
//    private final MessageBus messageBus;
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

    public void load() {
        //System.out.println("LOAD");
        List<MyModel> modelCollection = new ArrayList<>();
        List<MyModelBase> load = this.state.load();
        if (load == null) {
            //System.out.println("LOAD - null");
            return;
        }
        load.forEach(myModelBase -> {
            MyModel myModel = new MyModel();
            TargetBranchMap targetBranchMap = myModelBase.targetBranchMap;
            if (targetBranchMap == null) {
                //System.out.println("LOAD - targetBranchMap null");
                return;
            }
            //System.out.println("LOAD! - " + targetBranchMap.getValue());
            myModel.setTargetBranchMap(targetBranchMap);
            modelCollection.add(myModel);
        });
        setCollection(modelCollection);
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

//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        Platform.runLater(() -> {
//
//                });
        EventQueue.invokeLater(this::initLater);
//        SwingUtilities.invokeLater(this::initLater);
//        initLater();
    }

    public void initLater() {

        load();
        List<MyModel> modelCollection = getCollection();
//        if (collection.size() == 0) {
//            //System.out.println("no data exists - create new default");
        // new default "set"
//            addModel();
//            save();
//        }
//        if (collection.size() == 0) {
//            //System.out.println("stop");
//            return;
//        }
        //            //System.out.println("==");
        //            //System.out.println(model.getTargetBranch().getValue());
        //System.out.println("=== collectChanges, subscribeToObservable after load");
//        modelCollection.forEach(this::collectChanges);
        modelCollection.forEach(this::subscribeToObservable);

        initTabs();
        setActiveModel();
    }

    public void initTabs() {

        //System.out.println("=== InitTabs");
        List<MyModel> collection = this.getCollection();
        int size = collection.size();
//        if (size == 0) {
//            //System.out.println("initTabs stop");
//            return;
//        }

        initHeadTab();

        if (size > 0) {
            //System.out.println("init " + size + " tabs");
            collection.forEach(model -> {
                String tabName = getTargetBranchDisplay(model);
                toolWindowService.addTab(model, tabName, true);
//            changesService.collectChanges(model.getTargetBranch(), model::setChanges);
            });
        }
        toolWindowService.addListener();
        addPlusTab();
    }

    private void initHeadTab() {
        this.myHeadModel = new MyModel(true);
        toolWindowService.addTab(myHeadModel, GitService.BRANCH_HEAD, false);
        subscribeToObservable(myHeadModel);
        collectChanges(myHeadModel);
    }

    public void addRevisionTab(String revision) {
        String tabName = revision;
        MyModel myModel = addTabAndModel(tabName);
//        TargetBranchMap tbm = TargetBranchMap.create();
        gitService.getRepositories().forEach(repo -> {
//            tbm.add(repo.toString(), revision);
            //System.out.println("rev tab + " + repo + " - " + revision);
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
        //System.out.println("currrrr" + currentTabIndex);
        //System.out.println("lastTabIndex" + lastTabIndex);
        int toggleToIndex = (currentTabIndex == 0) ? lastTabIndex : 0;
        //System.out.println("toggle to " + toggleToIndex);
        setTabIndex(toggleToIndex);
        setActiveModel();
        toolWindowService.selectTabByIndex(toggleToIndex);
    }

    private void subscribeToObservable(MyModel model) {
        Observable<MyModel.field> observable = model.getObservable();
//        observable.onErrorComplete(e -> {
//
//        });
        observable.
//                debounce(500, TimeUnit.MILLISECONDS).
        subscribe(field -> {
    //System.out.println("MODELEVENT " + field);
    switch (field) {
        case targetBranch -> {
            //System.out.println("targetBranch");
//                    Boolean isHead = targetBranchService.isHeadActually(model.get());
//                    //System.out.println("isHed" + isHead);
            toolWindowService.changeTabName(getTargetBranchDisplay(model));
//                    model.getTargetBranch().get()
//                    Collection<Change> changes = changesService.getOnlyLocalChanges();
            collectChanges(model);
//                    model.setChanges(changes);
            save();
        }
        case changes -> {
//                            //System.out.println("changes");
//                    if (changesBefore != null && changesBefore.equals(changes)) {
//                        changesAreTheSame = true;
//                    }
//                    if (model.isHeadTab()) {
//                        return;
//                    }
//                    boolean isSameModel = model == getCurrent();
            if (model.isActive()) {

                Collection<Change> changes = model.getChanges();
                doUpdateDebounced(changes);
//                this.currentModel.setChanges(changes);
//                //System.out.println("!!!doUpdate (case changes)");
//                doUpdate(model);
            }
        }
        case active -> {
//                            //System.out.println("!!!doUpdate (case active)");

            collectChanges(model);
//                            doUpdate(model);
        }
    }

}, (e -> {
    //System.out.println(">>> EXCEPTION: " + e.getMessage());
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
            //System.out.println("Model is null");
            return;
        }
        TargetBranchMap targetBranchMap = model.getTargetBranchMap();
        if (targetBranchMap == null) {
            //System.out.println("CCC Collect Changes (null)");
        } else {
            //System.out.println("CCC Collect Changes " + targetBranchMap.getValue());
        }
        changesService.collectChangesWithCallback(targetBranchMap, model::setChanges);
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
        //System.out.println("getCurrent by ModelIndex " + currentModelIndex);
        int size = collection.size();
        //System.out.println("size: " + size);
//        if (size == 0) {
//            return myHeadModel;
//        }
        if (currentModelIndex >= size) {
            //System.out.println("!WARNING: out of bound");
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

    public void save() {
        //System.out.println("save");
        state.save(this.collection);
    }

    public void removeTab(int tabIndex) {
        //System.out.println("removed by tabIndex " + tabIndex);
        this.collection.remove(getModelIndex(tabIndex));
        save();
    }

    public int getTabIndex() {
        return currentTabIndex;
    }

    public void setTabIndex(int index) {
        //System.out.println("tab index " + index);
        // @todo need ID instead of index
        if (currentTabIndex > 0) {
            lastTabIndex = currentTabIndex;
        }
        //System.out.println("set lastTab" + currentTabIndex);
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

        //System.out.println("@@@ onUpdate");
        updateStatusBarWidget();
//        Collection<Change> changes = model.getChanges();
        //System.out.println(changes);
        if (changes == null) {
            //System.out.println("stop onUpdate");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            myLineStatusTrackerImpl.update(changes, null);
            this.myScope.update(changes);
            // @todo seems to pop up at start
//            licenseCheck();
        });
    }

    private void updateStatusBarWidget() {
        this.statusBarService.updateText(Defs.APPLICATION_NAME + ": " + getTargetBranchDisplayCurrent());
    }

    public void licenseCheck() {
        final Boolean isLicensed = CheckLicense.isLicensed();

        if (!Boolean.TRUE.equals(isLicensed)) {
//            final String message = "Unfortunately, you have not obtain the license for GitScope yet.";
//            JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, "GitScope requires a License", JOptionPane.INFORMATION_MESSAGE);
            CheckLicense.requestLicense("Please register our plugin!");
        }
    }
}