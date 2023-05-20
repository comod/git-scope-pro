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
import example.ToolWindowView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ToolWindowService implements ToolWindowServiceInterface {
    private final Project project;

    public ToolWindowService(Project project) {
        this.project = project;

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
        ContentManager contentManager = getContentManager();
//        contentManager.getComponent();
        contentManager.addContent(content);

        int index = contentManager.getIndexOfContent(content);
        System.out.println("addTab " + tabName + " with index " + index);
    }


    public void addListener() {
        getContentManager().addContentManagerListener(new MyTabContentListener(project));
    }

    @Override
    public void changeTabName(String title) {
        getToolWindow().setTitle(title);
//        setClosable();
    }

//    public void setClosable() {
//        @Nullable Content currentTabContent = getContentManager().getSelectedContent();
//        if (currentTabContent == null) {
//            return;
//        }
//        currentTabContent.setCloseable(true);
//    }

    public void removeTab(int index) {
        @Nullable Content content = getContentManager().getContent(index);
        if (content == null) {
            return;
        }
        getContentManager().removeContent(content, false);
    }

    public void removeCurrentTab() {
        @Nullable Content content = getContentManager().getSelectedContent();
        if (content == null) {
            return;
        }
        getContentManager().removeContent(content, true);
    }

    public void selectNewTab() {
        int count = getContentManager().getContentCount();
        @Nullable Content content = getContentManager().getContent(count - 2);
        if (content == null) {
            return;
        }
        getContentManager().setSelectedContent(content);
        getContentManager().requestFocus(content, true);
    }

//    public void selectHeadTab() {
//        int count = getContentManager().getContentCount();
//        @Nullable Content content = getContentManager().getContent(0);
//        if (content == null) {
//            return;
//        }
//        getContentManager().setSelectedContent(content);
//        getContentManager().requestFocus(content, true);
//    }

}
