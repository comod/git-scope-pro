package service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
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

    public void addTab() {
        ToolWindowManager toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("Git Scope");

        Manager manager = project.getService(Manager.class);
        assert manager != null;
        ContentFactory contentFactory = new ContentFactoryImpl();
        String tabName = "TabName";
//        String tabName = null;

        ViewService viewService = project.getService(ViewService.class);
        TabRecord tabRecord = new TabRecord(tabName);
        ToolWindowView toolWindowView = new ToolWindowView(project, viewService.getModel(tabRecord));
        Content content = ContentFactory.getInstance().createContent(toolWindowView.getContentPanel(), tabName, false);
        assert toolWindow != null;
        toolWindow.getContentManager().addContent(content);
    }
}
