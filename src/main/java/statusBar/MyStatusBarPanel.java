package statusBar;


import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBScalableIcon;

import javax.swing.*;
import java.awt.*;

public class MyStatusBarPanel extends JPanel {
    private final JBLabel hi;
    private final Component icon;
//    private final Icon icon;

    public MyStatusBarPanel() {

        this.hi = new JBLabel("");
        this.icon = getIcon();
        this.add(icon);
        this.add(hi);
    }

    private Component getIcon() {
        Icon icon = system.Defs.ICON;
//        ClassLoader cldr = this.getClass().getClassLoader();
//        java.net.URL imageURL = cldr.getResource("loading.png");
//        assert imageURL != null;
//        ImageIcon imageIcon = new ImageIcon(imageURL);
        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(icon);
//        imageIcon.setImageObserver(iconLabel);

        return iconLabel;

    }

    public void updateText(String text) {
        hi.setText(text);
    }
}
