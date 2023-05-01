package example;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Map;

public class BranchTree extends JPanel {

    private final Tree myTree;
    private final SearchTextField search;
    private final Map<String, List<FavLabel>> nodes;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode root;

    public BranchTree(Map<String, List<FavLabel>> nodes, SearchTextField search) {
        this.nodes = nodes;
        this.search = search;
        this.setLayout(new VerticalFlowLayout());

//        DefaultMutableTreeNode root = createTreeStructure(nodes, search);
        this.root = new DefaultMutableTreeNode();
        this.model = new DefaultTreeModel(root);
        this.myTree = new Tree(model);
        expandAllNodes(myTree);
        myTree.setRootVisible(false);
//        applySearchFilter(root);
        myTree.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        add(myTree);
        myTree.setCellRenderer(new MyColoredTreeCellRenderer());
        myTree.addTreeSelectionListener(treeSelectionEvent -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) myTree.getLastSelectedPathComponent();
            if (node == null) return;
            Object object = node.getUserObject();
            if (object instanceof FavLabel favLabel) {
                System.out.println(favLabel);
            }
        });
        update(search);
    }

    @NotNull
    private static DefaultMutableTreeNode createTreeStructure(
            Map<String, List<FavLabel>> nodes, SearchTextField search
    ) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
//        this.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        nodes.forEach((nodeLabel, list) -> {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeLabel);
            list.forEach(e -> {
                if (e.getName().contains(search.getText())) {
                    node.add(new DefaultMutableTreeNode(e));
                }
            });
            root.add(node);
        });
        return root;
    }

    public void update(SearchTextField search) {
        DefaultMutableTreeNode newRoot = createTreeStructure(nodes, search);
        model.setRoot(newRoot);
        model.reload(root);
        expandAllNodes(myTree);
    }

    public JTree getTreeComponent() {
        return this.myTree;
    }

    private void expandAllNodes(JTree tree) {
        expandAllNodes(tree, 0, 0);

    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }

        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
//        private final SearchTextField search;
//
//        MyColoredTreeCellRenderer(SearchTextField search) {
//            this.search = search;
//        }

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
//            String mySearch = search.getText();
//            if (!string.contains(mySearch)) {
//                tree.isVisible();
//                return;
//            }
            if (userObject == null) {
                return;
            }
            if (userObject instanceof FavLabel projectTemplate) {
                setIcon((projectTemplate.isFav() ? AllIcons.Nodes.Favorite : AllIcons.Vcs.Branch));
            }

            String string = userObject.toString();
            append(string);
//            append(string, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
//            setVisible(false);

//            SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, (SimpleColoredComponent) this, false, selected);
        }
    }
}
