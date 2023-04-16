package service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
import example.MyModel;
import example.TabRecord;
import example.ToolWindowView;
import example.ViewService;
import implementation.Manager;

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

    public void addTab(MyModel myModel, String tabName) {

        Manager manager = project.getService(Manager.class);
        assert manager != null;
        ToolWindowView toolWindowView = new ToolWindowView(project, myModel);
        Content content = ContentFactory.getInstance().createContent(toolWindowView.getContentPanel(), tabName, false);
        getToolWindow().getContentManager().addContent(content);
    }

    @Override
    public void changeTabName(String title) {
        getToolWindow().setTitle(title);
    }


}
