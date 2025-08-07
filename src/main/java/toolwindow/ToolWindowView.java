package toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import model.MyModel;
import org.jdesktop.swingx.StackLayout;
import toolwindow.elements.VcsTree;
import java.util.Collection;

import javax.swing.*;
import java.awt.*;

public class ToolWindowView {

    private final MyModel myModel;
    private final Project project;
    private final JPanel rootPanel = new JPanel(new StackLayout());

    private VcsTree vcsTree;
    private JPanel sceneA;
    private JPanel sceneB;

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
        this.myModel = myModel;

        myModel.getObservable().subscribe(model -> render());
        draw();
        render();
    }

    private void draw() {
        this.sceneA = getBranchSelectPanel();
        this.sceneB = getChangesPanel();
        rootPanel.add(sceneA);
        rootPanel.add(sceneB);
    }

    private JPanel getBranchSelectPanel() {
        BranchSelectView branchSelectPanel = new BranchSelectView(project);
        return branchSelectPanel.getRootPanel();
    }

    private JPanel getChangesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new StackLayout());

        vcsTree = new VcsTree(this.project);
        vcsTree.setLayout(new BorderLayout());

        panel.add(vcsTree);
        return panel;
    }

    private void render() {
        boolean myModelIsNew = myModel.isNew();
        boolean isHeadTab = myModel.isHeadTab();
        boolean showSceneA = myModelIsNew && !isHeadTab;
        sceneA.setVisible(showSceneA);
        sceneB.setVisible(!showSceneA);
        Collection<Change> modelChanges = myModel.getChanges();
        vcsTree.update(modelChanges);
    }

    public VcsTree getVcsTree() {
        return this.vcsTree;
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public MyModel getModel() {
        return myModel;
    }

}
