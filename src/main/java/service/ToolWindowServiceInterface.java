package service;

import example.MyModel;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName);

    void changeTabName(String title);
}
