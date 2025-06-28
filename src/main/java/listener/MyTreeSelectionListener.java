package listener;

import com.intellij.openapi.project.Project;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import com.intellij.ui.treeStructure.Tree;
import service.GitService;
import state.State;
import toolwindow.BranchSelectView;
import toolwindow.elements.BranchTreeEntry;
import service.ViewService;

public class MyTreeSelectionListener implements TreeSelectionListener {
    private final Tree tree;
    private final ViewService viewService;
    private final State state;
    private final GitService gitService;

    public MyTreeSelectionListener(Project project, Tree myTree) {
        this.tree = myTree;
        this.viewService = project.getService(ViewService.class);
        this.state = project.getService(State.class);
        this.gitService = project.getService(GitService.class);
    }

    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) return;
        Object object = node.getUserObject();
        if (object instanceof BranchTreeEntry favLabel) {
            String branchName = favLabel.getName();
            if (this.state.getTwoDotsCheckbox()) {
                String currentBranch = this.gitService.getCurrentBranchName();
                String twoDots = "..";
                String head = "HEAD";
                branchName = branchName + twoDots + head;
            }
            // Check if this is HEAD - if so, close current tab and switch to HEAD tab
            if (GitService.BRANCH_HEAD.equals(branchName)) {
                // Remove the current "New*" tab first
                this.viewService.removeCurrentTab();
                this.viewService.addRevisionTab(branchName); // This will switch to HEAD tab

            } else {
                this.viewService.getCurrent().addTargetBranch(favLabel.getGitRepo(), branchName);
            }
        }
    }
}
