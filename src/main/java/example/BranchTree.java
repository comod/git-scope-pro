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
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

public class BranchTree extends JPanel {

    public BranchTree(String rootLabel, List<FavLabel> iconLabels) {
        this.setLayout(new VerticalFlowLayout());
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootLabel);
        iconLabels.forEach(e -> {
            root.add(new DefaultMutableTreeNode(e));
        });
        Tree myTree = new Tree(root);
        myTree.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        add(myTree);
        myTree.setCellRenderer(new MyColoredTreeCellRenderer());
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
