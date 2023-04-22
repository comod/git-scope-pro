package service;

import com.intellij.ui.content.ContentManager;
import model.MyModel;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName);

    void changeTabName(String title);

    void addListener();
}
