package service;

import statusBar.MyStatusBarPanel;

public class StatusBarService {
    private final MyStatusBarPanel panel;

    public StatusBarService() {
        this.panel = new MyStatusBarPanel();
    }

    public MyStatusBarPanel getPanel() {
        return panel;
    }

    public void updateText(String text) {
        panel.updateText(text);
    }
}
