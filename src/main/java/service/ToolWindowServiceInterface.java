package service;

import model.MyModel;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName, boolean closeable);

    void changeTabName(String title);

    void addListener();

//    void setClosable();

    void removeTab(int index);

    void removeCurrentTab();

    void selectNewTab();

    void selectTabByIndex(int index);
}
