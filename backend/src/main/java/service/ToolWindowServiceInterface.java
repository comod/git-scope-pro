package service;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import model.MyModel;
import toolwindow.elements.VcsTree;

public interface ToolWindowServiceInterface {
    void addTab(MyModel myModel, String tabName, boolean closeable);

    void changeTabName(String title);

    void changeTabNameForModel(MyModel model, String title);

    void setupTabTooltip(MyModel model);

    void addListener();

    void removeAllTabs();

    void removeTab(int index);

    void removeCurrentTab();

    void selectNewTab();

    void selectTabByIndex(int index);

    VcsTree getVcsTree();

    void selectFile(VirtualFile file);

    /**
     * Paths of the displayed changes in the order the tool window shows them, or an empty list when
     * it has no tree yet. Safe to call from any thread.
     */
    java.util.List<String> getDisplayOrderedPaths();

    ToolWindow getToolWindow();

    MyModel getModelForContent(com.intellij.ui.content.Content content);
}
