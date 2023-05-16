package listener;

import com.intellij.openapi.project.Project;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import com.intellij.ui.treeStructure.Tree;
import example.BranchTreeEntry;
import example.ViewService;

public class MyTreeSelectionListener implements TreeSelectionListener {
    private final Tree tree;
    private final ViewService viewService;

    public MyTreeSelectionListener(Project project, Tree myTree) {
        this.tree = myTree;

        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) return;
        Object object = node.getUserObject();
//        MyModel currentModel = viewService.getCurrent();
        if (object instanceof BranchTreeEntry favLabel) {
//            currentModel.setTargetBranch(favLabel.getName());
            System.out.println(favLabel);
            this.viewService.getCurrent().addTargetBranch(favLabel.getGitRepo(), favLabel.getName());
        }

        if (object instanceof String label) {
            System.out.println(label);
        }
    }
}
