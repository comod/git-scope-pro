package example;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.panels.HorizontalBox;
import implementation.targetBranchWidget.PopUpFactory;
import model.MyModel;

import service.TargetBranchService;
import ui.elements.VcsTree;

public class ToolWindowView {

    private final JPanel rootPanel = new JPanel();
    private final MyModel myModel;
    private final Project project;
    private final TargetBranchService targetBranchService;

    JButton tbp = new JButton();

    private VcsTree vcsTree;

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
//        this.state = State.getInstance(project);
        this.myModel = myModel;
        this.targetBranchService = project.getService(TargetBranchService.class);

        myModel.getObservable().subscribe(model -> {
            render();
        });

        draw();

        addListener();

        render();
    }

    private void draw() {
        VerticalStackLayout verticalStackLayout = new VerticalStackLayout();
        rootPanel.setLayout(verticalStackLayout);
        HorizontalBox hBox = new HorizontalBox();

        hBox.add(tbp);

        vcsTree = new VcsTree(this.project);
        vcsTree.setLayout(new VerticalFlowLayout());

        rootPanel.add(hBox);
        rootPanel.add(vcsTree);
    }

    private void addListener() {
        tbp.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                PopUpFactory f = project.getService(PopUpFactory.class);
                f.showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });
    }

    private void render() {
//        if (myModel.getTargetBranch() == null) {
        tbp.setText(this.targetBranchService.getTargetBranchDisplay(myModel.getTargetBranch()));
//        } else {
//            tbp.hide();
//        }
        if (myModel.getChanges() == null) {
            return;
        }
        vcsTree.update(myModel.getChanges());
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }
}
