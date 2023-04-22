package example;

import com.intellij.openapi.project.Project;
import model.MyModel;
import model.MyModelBase;
import org.jetbrains.annotations.NotNull;
import service.ChangesService;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;
import state.State;

import java.util.ArrayList;
import java.util.List;

public class ViewService {

    private final Project project;
    private final ToolWindowServiceInterface toolWindowService;
    private final TargetBranchService targetBranchService;
    private final ChangesService changesService;
    private final State state;

    public List<MyModel> collection = new ArrayList<>();

    public Integer currentTabIndex = 0;

    private boolean vcsReady = false;
    private boolean toolWindowReady = false;

    public ViewService(Project project) {
        this.project = project;
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = project.getService(State.class);
    }

    public void load() {
        List<MyModel> modelCollection = new ArrayList<>();
        List<MyModelBase> load = this.state.load();
        if (load == null) {
            return;
        }
        load.forEach(myModelBase -> {
            MyModel myModel = new MyModel();
            myModel.setTargetBranch(myModelBase.targetBranch);
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
        load();
        List<MyModel> collection = getCollection();
        if (collection.size() == 0) {
//            System.out.println("new default set");
            // new default "set"
            MyModel model = createModel();
            addModel(model);
//            save();
        }
        if (collection.size() == 0) {
//            System.out.println("stop");
            return;
        }
        //            System.out.println("==");
        //            System.out.println(model.getTargetBranch().getValue());
        collection.forEach(this::collectChanges);
        collection.forEach(this::subscribeToObservable);

        initTabs();
    }


    public void initTabs() {

        List<MyModel> collection = this.getCollection();
        if (collection.size() == 0) {
            System.out.println("initTabs stop");
            return;
        }
        collection.forEach(model -> {
            String tabName = getTargetBranchDisplay(model);
            toolWindowService.addTab(model, tabName);
//            changesService.collectChanges(model.getTargetBranch(), model::setChanges);
        });

        toolWindowService.addListener();
    }

    private void mayAddPlusTab() {
        toolWindowService.addTab(new MyModel(), "+");
    }

    @NotNull
    private MyModel createModel() {
        return new MyModel();
    }

    private void subscribeToObservable(MyModel model) {
        model.getObservable().subscribe(field -> {
            switch (field) {
                case targetBranch -> {

                    Boolean isHead = targetBranchService.isHeadActually(model.getTargetBranch());
                    System.out.println("isHed" + isHead);

                    toolWindowService.changeTabName(getTargetBranchDisplay(model));
//                    model.getTargetBranch().get()
//                    Collection<Change> changes = changesService.getOnlyLocalChanges();
                    collectChanges(model);
//                    model.setChanges(changes);
                    save();
                    mayAddPlusTab();
                }
//                case changes -> System.out.println("changes!!:" + field);
            }

        });
    }


    private String getTargetBranchDisplay(MyModel model) {
        return this.targetBranchService.
                getTargetBranchDisplay(model.getTargetBranch());
    }

    private void collectChanges(MyModel model) {
        changesService.collectChanges(model.getTargetBranch(), model::setChanges);
    }

    public void addModel(MyModel model) {
        this.collection.add(model);
    }

    public MyModel getCurrent() {
        return this.getCollection().get(currentTabIndex);
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
}