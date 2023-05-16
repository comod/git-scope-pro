package example;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import event.ChangeActionNotifierInterface;
import model.MyModel;
import model.MyModelBase;
import service.ChangesService;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;
import state.State;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ViewService {

    public static final String PLUS_TAB_LABEL = "+";
    private final Project project;
    private final ToolWindowServiceInterface toolWindowService;
    private final TargetBranchService targetBranchService;
    private final ChangesService changesService;
    private final State state;
//    private final MessageBusConnection messageBusConnection;

    public List<MyModel> collection = new ArrayList<>();

    public Integer currentTabIndex = 0;

    private boolean vcsReady = false;
    private boolean toolWindowReady = false;

    private MessageBus messageBus;
    private MyModel myHeadModel;

    public ViewService(Project project) {
        this.project = project;
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = project.getService(State.class);

        this.messageBus = this.project.getMessageBus();
//        this.messageBusConnection = messageBus.connect();
    }

    public void load() {
        List<MyModel> modelCollection = new ArrayList<>();
        List<MyModelBase> load = this.state.load();
        if (load == null) {
            return;
        }
        load.forEach(myModelBase -> {
            MyModel myModel = new MyModel();
            myModel.setTargetBranchMap(myModelBase.targetBranchMap);
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
        SwingUtilities.invokeLater(this::initLater);
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
        modelCollection.forEach(this::collectChanges);
        modelCollection.forEach(this::subscribeToObservable);

        initTabs();
        setActiveModel();
    }


    public void initTabs() {

        List<MyModel> collection = this.getCollection();
        int size = collection.size();
//        if (size == 0) {
//            System.out.println("initTabs stop");
//            return;
//        }

        this.myHeadModel = new MyModel(true);
        toolWindowService.addTab(myHeadModel, "HEAD", false);
        subscribeToObservable(myHeadModel);
        collectChanges(myHeadModel);

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

    private void addPlusTab() {
        toolWindowService.addTab(new MyModel(), PLUS_TAB_LABEL, false);
    }

    public void plusTabClicked() {
//        toolWindowService.changeTabName("New*");
//        MyModel model = addModel();
        int currentIndexOfPlusTab = getTabIndex();
        toolWindowService.addTab(addModel(), "New*", true);
        addPlusTab();
//        toolWindowService.removeCurrentTab();
        toolWindowService.selectNewTab();

        SwingUtilities.invokeLater(() -> toolWindowService.removeTab(currentIndexOfPlusTab));

    }

    private void subscribeToObservable(MyModel model) {
        model.getObservable().subscribe(field -> {
            System.out.println("model saved");
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
                    System.out.println("changes");
//                    if (changesBefore != null && changesBefore.equals(changes)) {
//                        changesAreTheSame = true;
//                    }
//                    if (model.isHeadTab()) {
//                        return;
//                    }
//                    boolean isSameModel = model == getCurrent();
                    if (model.isActive()) {
                        System.out.println("!!!changes set and this model is active yay");
                        dispatchChangesEvent(model);
                    }
                }
                case active -> {
                    System.out.println("!!!changes set and this model is active yay");
                    dispatchChangesEvent(model);
                }
            }

        });
    }

    private void dispatchChangesEvent(MyModel model) {
        ChangeActionNotifierInterface publisher = messageBus.syncPublisher(ChangeActionNotifierInterface.CHANGE_ACTION_TOPIC);
        publisher.doAction(model);
    }


    private String getTargetBranchDisplay(MyModel model) {
        return this.targetBranchService.
                getTargetBranchDisplay(model.getTargetBranchMap());
    }

    private void collectChanges(MyModel model) {
        changesService.collectChanges(model.getTargetBranchMap(), model::setChanges);
    }

    public MyModel addModel() {
        MyModel model = new MyModel();
        this.collection.add(model);
        subscribeToObservable(model);
        return model;
    }

    public MyModel getCurrent() {
        if (getTabIndex() == 0) {
            return myHeadModel;
        }
        System.out.println("getCurrent by ModelIndex " + getCurrentModelIndex());
        return this.getCollection().get(getCurrentModelIndex());
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
}