package service;

import com.intellij.openapi.Disposable;
import statusBar.MyStatusBarPanel;

public class StatusBarService implements Disposable {
    private MyStatusBarPanel panel;

    public StatusBarService() {
        this.panel = new MyStatusBarPanel();
    }

    public MyStatusBarPanel getPanel() {
        return panel;
    }

    public void updateText(String text) {
        if (panel != null) {
            panel.updateText(text);
        }
    }

    @Override
    public void dispose() {
        panel = null;
    }
}
