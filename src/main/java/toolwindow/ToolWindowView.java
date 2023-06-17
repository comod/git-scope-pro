package toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalBox;
import com.intellij.ui.components.panels.VerticalBox;
import model.MyModel;
import org.jdesktop.swingx.HorizontalLayout;
import org.jdesktop.swingx.StackLayout;
import toolwindow.elements.CurrentBranch;
import toolwindow.elements.TargetBranch;
import toolwindow.elements.VcsTree;

import javax.swing.*;
import java.awt.*;

public class ToolWindowView {

    private final MyModel myModel;
    private final Project project;
    private final CurrentBranch currentBranch;
    //    private final JPanel rootPanel = new JPanel(new VerticalStackLayout());
//    private final JPanel rootPanel = new JPanel(new BorderLayout());
//    private final JPanel rootPanel = new JPanel(new VerticalFlowLayout());

    private final JPanel rootPanel = new JPanel(new StackLayout());
    private final TargetBranch targetBranch;

    private VcsTree vcsTree;
    private JPanel sceneA;
    private JPanel sceneB;

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
//        this.state = State.getInstance(project);
        this.myModel = myModel;
//        this.targetBranchService = project.getService(TargetBranchService.class);

        this.currentBranch = new CurrentBranch(project);
        this.targetBranch = new TargetBranch(project);

        myModel.getObservable().subscribe(model -> {
            render();
        });

        draw();
//        drawNew();

        render();
    }

    private void draw() {
//        VerticalBox sceneBox = new VerticalBox();
        this.sceneA = getBranchSelectPanel();
        this.sceneB = getChangesPanel();
        rootPanel.add(sceneA);
        rootPanel.add(sceneB);
//        rootPanel.setLayout(new VerticalFlowLayout());
//        rootPanel.add(sceneBox);

    }

//    private void drawNew() {
////        JPanel panel = getBranchSelectPanel();
//        JPanel panel = getChangesPanel();
//        rootPanel = panel;
//    }

    private JPanel getBranchSelectPanel() {
        BranchSelectView branchSelectPanel = new BranchSelectView(project);
        return branchSelectPanel.getRootPanel();
    }


    private JPanel getChangesPanel() {
        JPanel panel = new JPanel();
//        panel.setLayout(new VerticalStackLayout());
        panel.setLayout(new StackLayout());

        vcsTree = new VcsTree(this.project);
        vcsTree.setLayout(new BorderLayout());

        panel.add(vcsTree);
        return panel;
    }

    private void render() {
        boolean myModelIsNew = myModel.isNew();
        boolean isHeadTab = myModel.isHeadTab();
        System.out.println("===render toolWindowView (myModelIsNew: " + myModelIsNew + " isHeadTab:" + isHeadTab);
        boolean showSceneA = myModelIsNew && !isHeadTab;
        sceneA.setVisible(showSceneA);
        sceneB.setVisible(!showSceneA);

//        if (myModel.getTargetBranch() == null) {
//        tbp.setText(this.targetBranchService.getTargetBranchDisplay(myModel.getTargetBranch()));
//        } else {
//            tbp.hide();
//        }
        if (myModel.getChanges() == null) {
            System.out.println("no changes to show");
            return;
        }
        System.out.println("render/update changes" + myModel.getChanges().toString());
        vcsTree.update(myModel.getChanges());
//        if (!showSceneA) {
//            this.targetBranch.update(myModel);
//        }
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

}
