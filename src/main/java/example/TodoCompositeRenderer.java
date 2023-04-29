package example;

import javax.swing.tree.TreeCellRenderer;


import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.nodes.SummaryNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.HighlightableCellRenderer;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

final class TodoCompositeRenderer implements TreeCellRenderer {
    private final NodeRenderer myNodeRenderer;
    private final HighlightableCellRenderer myColorTreeCellRenderer;

    TodoCompositeRenderer() {
        myNodeRenderer = new NodeRenderer();
        myColorTreeCellRenderer = new HighlightableCellRenderer();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object obj, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Component result;

        Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
        if (userObject instanceof SummaryNode) {
            myNodeRenderer.getTreeCellRendererComponent(tree, userObject.toString(), selected, expanded, leaf, row, hasFocus);
            myNodeRenderer.setFont(UIUtil.getTreeFont().deriveFont(Font.BOLD));
            myNodeRenderer.setIcon(null);
            result = myNodeRenderer;
        } else if (userObject instanceof NodeDescriptor descriptor && userObject instanceof HighlightedRegionProvider regionProvider) {
            myColorTreeCellRenderer.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);
            for (HighlightedRegion region : regionProvider.getHighlightedRegions()) {
                myColorTreeCellRenderer.addHighlighter(region.startOffset, region.endOffset, region.textAttributes);
            }
            myColorTreeCellRenderer.setIcon(descriptor.getIcon());
            result = myColorTreeCellRenderer;
        } else {
            result = myNodeRenderer.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);
        }

        ((JComponent) result).setOpaque(!selected);

        return result;
    }
}