package example;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import implementation.compare.ChangesService;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import io.reactivex.rxjava3.core.Observable;
import model.Debounce;
import model.MyModel;
import model.MyModelBase;
import model.valueObject.TargetBranchMap;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;
import state.State;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ViewService {

    public static final String PLUS_TAB_LABEL = "+";
    public static final int DEBOUNCE_MS = 50;
    private final Project project;
    private final ToolWindowServiceInterface toolWindowService;
    private final TargetBranchService targetBranchService;
    private final ChangesService changesService;
    private final State state;
    //    private final MessageBusConnection messageBusConnection;
//        private final MessageBusConnection messageBusConnection;
//    private final MessageBus messageBus;
    private final MyLineStatusTrackerImpl myLineStatusTrackerImpl;
    private final Debounce debouncer;
    //    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public List<MyModel> collection = new ArrayList<>();
    public Integer currentTabIndex = 0;
    private boolean vcsReady = false;
    private boolean toolWindowReady = false;
    private MyModel myHeadModel;

    public ViewService(Project project) {
        this.project = project;
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = project.getService(State.class);
        this.myLineStatusTrackerImpl = new MyLineStatusTrackerImpl(project);
        this.debouncer = new Debounce();
//        this.messageBus = this.project.getMessageBus();
//        this.messageBusConnection = messageBus.connect();
    }

    private void doUpdateDebounced(Collection<Change> changes) {
        debouncer.debounce(Void.class, () -> doUpdate(changes), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void load() {
        System.out.println("LOAD");
        List<MyModel> modelCollection = new ArrayList<>();
        List<MyModelBase> load = this.state.load();
        if (load == null) {
            System.out.println("LOAD - null");
            return;
        }
        load.forEach(myModelBase -> {
            MyModel myModel = new MyModel();
            TargetBranchMap targetBranchMap = myModelBase.targetBranchMap;
            if (targetBranchMap == null) {
                System.out.println("LOAD - targetBranchMap null");
                return;
            }
            System.out.println("LOAD! - " + targetBranchMap.getValue());
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
        if (!vcsReady || !toolWindowReady) {
            return;
        }

//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        Platform.runLater(() -> {
//
//                });
        SwingUtilities.invokeLater(this::initLater);
//        initLater();
    }

    public void initLater() {

        load();
        List<MyModel> modelCollection = getCollection();
//        if (collection.size() == 0) {
//            System.out.println("no data exists - create new default");
        // new default "set"
//            addModel();
//            save();
//        }
//        if (collection.size() == 0) {
//            System.out.println("stop");
//            return;
//        }
        //            System.out.println("==");
        //            System.out.println(model.getTargetBranch().getValue());
        System.out.println("=== collectChanges, subscribeToObservable after load");
//        modelCollection.forEach(this::collectChanges);
        modelCollection.forEach(this::subscribeToObservable);

        initTabs();
        setActiveModel();
    }

    public void initTabs() {

        System.out.println("=== InitTabs");
        List<MyModel> collection = this.getCollection();
        int size = collection.size();
//        if (size == 0) {
//            System.out.println("initTabs stop");
//            return;
//        }

        initHeadTab();

        if (size > 0) {
            System.out.println("init " + size + " tabs");
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
        toolWindowService.addTab(myHeadModel, "HEAD", false);
        subscribeToObservable(myHeadModel);
        collectChanges(myHeadModel);
    }

    private void addPlusTab() {
        toolWindowService.addTab(new MyModel(), PLUS_TAB_LABEL, false);
    }

    public void plusTabClicked() {
//        toolWindowService.changeTabName("New*");
//        MyModel model = addModel();
        int currentIndexOfPlusTab = getTabIndex();
        toolWindowService.addTab(addModel(), "New*", true);
//            toolWindowService.removeCurrentTab();
        addPlusTab();
        toolWindowService.selectNewTab();
        toolWindowService.removeTab(currentIndexOfPlusTab);
        setTabIndex(collection.size());
        setActiveModel();
//        SwingUtilities.invokeLater(toolWindowService::selectNewTab);
//        SwingUtilities.invokeLater(() -> toolWindowService.removeTab(currentIndexOfPlusTab));

    }

    private void subscribeToObservable(MyModel model) {
        Observable<MyModel.field> observable = model.getObservable();
//        observable.onErrorComplete(e -> {
//
//        });
        observable.
//                debounce(500, TimeUnit.MILLISECONDS).
        subscribe(field -> {
    System.out.println("MODELEVENT " + field);
    switch (field) {
        case targetBranch -> {
            System.out.println("targetBranch");
//                    Boolean isHead = targetBranchService.isHeadActually(model.get());
//                    System.out.println("isHed" + isHead);
            toolWindowService.changeTabName(getTargetBranchDisplay(model));
//                    model.getTargetBranch().get()
//                    Collection<Change> changes = changesService.getOnlyLocalChanges();
            collectChanges(model);
//                    model.setChanges(changes);
            save();
        }
        case changes -> {
//                            System.out.println("changes");
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
//                System.out.println("!!!doUpdate (case changes)");
//                doUpdate(model);
            }
        }
        case active -> {
//                            System.out.println("!!!doUpdate (case active)");

            collectChanges(model);
//                            doUpdate(model);
        }
    }

}, (e -> {
    System.out.println(">>> EXCEPTION: " + e.getMessage());
}));

    }

    private String getTargetBranchDisplay(MyModel model) {
        return this.targetBranchService.
                getTargetBranchDisplay(model.getTargetBranchMap());
    }

    private void collectChanges(MyModel model) {
        TargetBranchMap targetBranchMap = model.getTargetBranchMap();
        if (targetBranchMap == null) {
            System.out.println("CCC Collect Changes (null)");
        } else {
            System.out.println("CCC Collect Changes " + targetBranchMap.getValue());
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
        System.out.println("getCurrent by ModelIndex " + currentModelIndex);
        int size = collection.size();
        System.out.println("size: " + size);
//        if (size == 0) {
//            return myHeadModel;
//        }
        if (currentModelIndex >= size) {
            System.out.println("!WARNING: out of bound");
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
        System.out.println("save");
        state.save(this.collection);
    }

    public void removeTab(int tabIndex) {
        System.out.println("removed by tabIndex " + tabIndex);
        this.collection.remove(getModelIndex(tabIndex));
        save();
    }

    public int getTabIndex() {
        return currentTabIndex;
    }

    public void setTabIndex(int index) {
        System.out.println("tab index " + index);
        this.currentTabIndex = index;
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

    public void doUpdate(Collection<Change> changes) {

        System.out.println("@@@ doUpdate");
//        Collection<Change> changes = model.getChanges();
        System.out.println(changes);
        if (changes == null) {
            System.out.println("stop doUpdate");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            myLineStatusTrackerImpl.update(changes, null);
        });
    }
}