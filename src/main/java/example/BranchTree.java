package example;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BranchTree extends JPanel {

    public BranchTree(Map<String, List<FavLabel>> nodes) {
        this.setLayout(new VerticalFlowLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
//        this.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        nodes.forEach((nodeLabel, list) -> {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeLabel);
            list.forEach(e -> {
                node.add(new DefaultMutableTreeNode(e));
            });
            root.add(node);
        });
        Tree myTree = new Tree(root);
        expandAllNodes(myTree, 0, 0);
        myTree.setRootVisible(false);
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
        myTree.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
                System.out.println(keyEvent);
            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {

            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {

            }
        });
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
//        MyColoredTreeCellRenderer() {
//            myUsedCustomSpeedSearchHighlighting = true;
//        }

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof FavLabel projectTemplate) {
                setIcon((projectTemplate.isFav() ? AllIcons.Nodes.Favorite : AllIcons.Vcs.Branch));
            }

            append(userObject.toString());

//            SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, (SimpleColoredComponent) this, false, selected);
        }
    }
}
