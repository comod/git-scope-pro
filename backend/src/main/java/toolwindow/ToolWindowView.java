package toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import model.MyModel;
import org.jdesktop.swingx.StackLayout;
import toolwindow.elements.VcsTree;
import java.util.Collection;
import java.util.function.Consumer;

import javax.swing.*;
import java.awt.*;

public class ToolWindowView implements Disposable {

    private final MyModel myModel;
    private final Project project;
    private final JPanel rootPanel = new JPanel(new StackLayout());

    private VcsTree vcsTree;
    private JPanel sceneA;
    private JPanel sceneB;
    private BranchSelectView branchSelectView;
    private final Consumer<MyModel.field> listener = field -> render();

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
        this.myModel = myModel;

        myModel.addListener(listener);
        draw();
        render();
    }

    @Override
    public void dispose() {
        // Remove the listener first to prevent further updates
        myModel.removeListener(listener);

        // Dispose BranchSelectView (removes listeners from trees, checkboxes, etc.)
        if (branchSelectView != null) {
            branchSelectView.dispose();
            branchSelectView = null;
        }

        // Explicitly cleanup VcsTree first (cancels futures, disposes browsers)
        if (vcsTree != null) {
            vcsTree.cleanup();
            vcsTree = null;
        }

        // Remove all components from panels to break JNI references
        if (sceneB != null) {
            sceneB.removeAll();
            sceneB = null;
        }
        if (sceneA != null) {
            sceneA.removeAll();
            sceneA = null;
        }
        rootPanel.removeAll();
    }

    private void draw() {
        this.sceneA = getBranchSelectPanel();
        this.sceneB = getChangesPanel();
        rootPanel.add(sceneA);
        rootPanel.add(sceneB);
    }

    private JPanel getBranchSelectPanel() {
        branchSelectView = new BranchSelectView(project);
        return branchSelectView.getRootPanel();
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
        if (modelChanges != null) {
            vcsTree.update(modelChanges);
        }
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
