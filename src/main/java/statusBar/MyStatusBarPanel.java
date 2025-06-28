package statusBar;

import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.ui.JBColor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import system.Defs;

import javax.swing.*;
import java.awt.*;

public class MyStatusBarPanel extends JPanel {
    private final TextPanel textLabel;
    private final JBLabel iconLabel;

    public MyStatusBarPanel() {
        super();

        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setAlignmentY(Component.CENTER_ALIGNMENT);

        // Create icon label with properly scaled icon
        Icon scaledIcon = IconUtil.scale(Defs.ICON, null, 0.75f);
        iconLabel = new JBLabel(scaledIcon);
        iconLabel.setOpaque(false);
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(Box.createHorizontalStrut(4));

        // Create text label with proper status bar styling
        textLabel =  new TextPanel() {};
        textLabel.setFont(SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label());
        textLabel.recomputeSize();

        add(iconLabel);
        add(Box.createHorizontalStrut(4));
        add(textLabel);
    }

    public void updateText(String text) {
        textLabel.setText(text);
        revalidate();
        repaint();
    }
}