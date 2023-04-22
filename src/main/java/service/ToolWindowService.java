package service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import listener.MyTabContentListener;
import model.MyModel;
import example.ToolWindowView;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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

    public void addTab(MyModel myModel, String tabName) {
        ToolWindowView toolWindowView = new ToolWindowView(project, myModel);
        Content content = ContentFactory.getInstance().createContent(toolWindowView.getRootPanel(), tabName, false);
        content.setCloseable(true);
        ContentManager contentManager = getContentManager();
        contentManager.addContent(content);
//        int index = contentManager.getIndexOfContent(content);
//        System.out.println("addTab " + tabName + "as index" + index);
    }


    public void addListener() {
        getContentManager().addContentManagerListener(new MyTabContentListener());
    }

    @Override
    public void changeTabName(String title) {
        getToolWindow().setTitle(title);
    }

}
