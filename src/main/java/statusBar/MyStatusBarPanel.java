package statusBar;


import com.intellij.ui.components.JBLabel;

import javax.swing.*;

public class MyStatusBarPanel extends JPanel {
    private final JBLabel hi;

    public MyStatusBarPanel() {
        this.hi = new JBLabel("Hi");
        this.add(hi);
    }

    public void updateText(String text) {
        hi.setText(text);
    }
}
