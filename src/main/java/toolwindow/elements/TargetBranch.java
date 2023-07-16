package toolwindow.elements;

import com.intellij.openapi.project.Project;
import model.MyModel;
import service.GitService;
import service.TargetBranchService;

import javax.swing.*;

public class TargetBranch extends JLabel {

    private final GitService git;
    private final TargetBranchService targetBranchService;

    public TargetBranch(Project project) {

        this.git = project.getService(GitService.class);

        this.targetBranchService = project.getService(TargetBranchService.class);
//        this.createElement();
        this.addListener();

    }

//    public void createElement() {
//        update();
//    }

    public void addListener() {

    }

    public void update(MyModel model) {
        String display = targetBranchService.getTargetBranchDisplay(model.getTargetBranchMap());
        setText(display);
    }

}
