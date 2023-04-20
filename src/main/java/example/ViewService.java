package example;

import com.intellij.openapi.project.Project;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import service.ChangesService;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;
import state.State;

import java.util.List;

public class ViewService {

    private final Project project;
    private final ToolWindowServiceInterface toolWindowService;
    private final TargetBranchService targetBranchService;
    private final ChangesService changesService;
    private final State state;


    public Integer currentTabIndex = 0;

    public ViewService(Project project) {
        this.project = project;
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.state = State.getInstance(project);
    }

    public void init() {
        List<MyModel> collection = state.getCollection();
        if (collection.size() == 0) {
            // new default "set"
            MyModel model = createModel();
            addModel(model);
        }

        collection.forEach(this::subscribeToObservable);
    }

    public void initTabs() {
        List<MyModel> collection = state.getCollection();
        collection.forEach(model -> {
            String tabName = "+";
            toolWindowService.addTab(model, tabName);
        });
    }

    @NotNull
    private MyModel createModel() {
        return new MyModel();
    }

    private void subscribeToObservable(MyModel model) {
        model.getObservable().subscribe(field -> {
            switch (field) {
//                case field1 -> System.out.println("yolo:" + field);
                case targetBranch -> {

                    Boolean isHead = targetBranchService.isHeadActually(model.getTargetBranch());
                    System.out.println("isHed" + isHead);

                    toolWindowService.changeTabName(
                            this.targetBranchService.
                                    getTargetBranchDisplay(model.getTargetBranch())
                    );
//                    model.getTargetBranch().get()
//                    Collection<Change> changes = changesService.getOnlyLocalChanges();
                    changesService.collectChanges(model.getTargetBranch(), model::setChanges);
//                    model.setChanges(changes);
                }
//                case changes -> System.out.println("changes!!:" + field);
            }

        });
    }

    public MyModel getCurrent() {
        return state.getCollection().get(currentTabIndex);
    }

    public void addModel(MyModel model) {
        state.addToCollection(model);
    }

}
