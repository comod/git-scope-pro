package example;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;


public class CalendarToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
//        ViewService viewService = project.getService(ViewService.class);
//        ToolWindowView tw = new ToolWindowView(project, viewService.getModel(ViewService.defaultRef));
//        Content content = ContentFactory.getInstance().createContent(tw.getContentPanel(), "A", false);
//        toolWindow.getContentManager().addContent(content);
//
//        TabRecord br = new TabRecord("Test");
//        ToolWindowView tw2 = new ToolWindowView(project, viewService.getModel(br));
//        Content content2 = ContentFactory.getInstance().createContent(tw2.getContentPanel(), "B", false);
//        toolWindow.getContentManager().addContent(content2);
    }
}
