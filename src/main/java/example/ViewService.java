package example;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import service.ChangesService;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ViewService {

    public static TabRecord defaultRef = new TabRecord(TabRecord.head);
    private final Project project;
    private final ToolWindowServiceInterface toolWindowService;
    private final TargetBranchService targetBranchService;
    private final ChangesService changesService;
    public Map<TabRecord, MyModel> map = new HashMap<>();

    public TabRecord currentTab;

    public ViewService(Project project) {
        this.project = project;
        this.toolWindowService = project.getService(ToolWindowServiceInterface.class);
        this.changesService = project.getService(ChangesService.class);
        this.targetBranchService = project.getService(TargetBranchService.class);
    }

    public void addTab() {
        String tabName = "TabName";
//        String tabName = null;

        ViewService viewService = project.getService(ViewService.class);
        TabRecord tabRecord = new TabRecord(tabName);
        MyModel model = viewService.getModel(tabRecord);
        toolWindowService.addTab(model, tabName);
        model.getObservable().subscribe(field -> {
            switch (field) {
//                case field1 -> System.out.println("yolo:" + field);
                case targetBranch -> {

                    toolWindowService.changeTabName(
                            this.targetBranchService.
                                    getTargetBranchDisplay(model.getTargetBranch())
                    );
                    Collection<Change> changes = changesService.getOnlyLocalChanges();
                    model.setChanges(changes);
                }
//                case changes -> System.out.println("changes!!:" + field);
            }

        });
    }

    public MyModel getModel(TabRecord tabRecord) {
        if (!this.map.containsKey(tabRecord)) {
            currentTab = tabRecord;
            MyModel model = new MyModel(tabRecord);
            setModel(tabRecord, model);
        }
        return this.map.get(tabRecord);
    }

    public MyModel getCurrent() {
        return getModel(currentTab);
    }

    public void setModel(TabRecord ref, MyModel model) {
        this.map.put(ref, model);
    }

}
