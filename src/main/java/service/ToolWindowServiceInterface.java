package service;

import model.MyModel;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName);

    void changeTabName(String title);
}
