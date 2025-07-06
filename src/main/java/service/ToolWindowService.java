package service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import listener.MyTabContentListener;
import model.MyModel;
import toolwindow.ToolWindowView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import toolwindow.elements.VcsTree;

import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class ToolWindowService implements ToolWindowServiceInterface {
    private final Project project;
    private final Map<Content, ToolWindowView> contentToViewMap = new HashMap<>();

    public ToolWindowService(Project project) {
        this.project = project;
    }

    @Override
    public void removeAllTabs() {
        getContentManager().removeAllContents(true);
        contentToViewMap.clear();
    }

    public ToolWindow getToolWindow() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("Git Scope");
        assert toolWindow != null;
        return toolWindow;
    }

    @NotNull
    private ContentManager getContentManager() {
        return getToolWindow().getContentManager();
    }

    public void addTab(MyModel myModel, String tabName, boolean closeable) {
        ToolWindowView toolWindowView = new ToolWindowView(project, myModel);
        Content content = ContentFactory.getInstance().createContent(toolWindowView.getRootPanel(), tabName, false);
        content.setCloseable(closeable);

        contentToViewMap.put(content, toolWindowView);

        ContentManager contentManager = getContentManager();
        contentManager.addContent(content);

        int index = contentManager.getIndexOfContent(content);
    }

    @Override
    public VcsTree getVcsTree() {
        try {
            // Get the currently selected content
            Content selectedContent = getContentManager().getSelectedContent();
            if (selectedContent == null) {
                return null;
            }

            // Get the ToolWindowView for this content
            ToolWindowView toolWindowView = contentToViewMap.get(selectedContent);
            if (toolWindowView != null) {
                return toolWindowView.getVcsTree();
            }

            return null;

        } catch (Exception e) {
            // Log the error if needed
            return null;
        }
    }


    public void addListener() {
        getContentManager().addContentManagerListener(new MyTabContentListener(project));
    }

    @Override
    public void changeTabName(String title) {
        getToolWindow().setTitle(title);
    }

    public void removeTab(int index) {
        @Nullable Content content = getContentManager().getContent(index);
        if (content == null) {
            return;
        }
        contentToViewMap.remove(content);
        getContentManager().removeContent(content, false);
    }

    public void removeCurrentTab() {
        @Nullable Content content = getContentManager().getSelectedContent();
        if (content == null) {
            return;
        }
        contentToViewMap.remove(content);
        getContentManager().removeContent(content, true);
    }

    public void selectNewTab() {
        int count = getContentManager().getContentCount();
        int index = count - 2;
        selectTabByIndex(index);
    }

    @Override
    public void selectTabByIndex(int index) {
        @Nullable Content content = getContentManager().getContent(index);
        if (content == null) {
            return;
        }
        getContentManager().setSelectedContent(content);
        getContentManager().requestFocus(content, true);
    }

}
