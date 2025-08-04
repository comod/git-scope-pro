package service;

import com.intellij.openapi.vfs.VirtualFile;
import model.MyModel;
import toolwindow.elements.VcsTree;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName, boolean closeable);

    void changeTabName(String title);

    void setupTabTooltip(MyModel model);

    void addListener();

    void removeAllTabs();

    void removeTab(int index);

    void removeCurrentTab();

    void selectNewTab();

    void selectTabByIndex(int index);

    VcsTree getVcsTree();

    void selectFile(VirtualFile file);
}
