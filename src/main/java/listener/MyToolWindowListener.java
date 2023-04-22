package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
//import implementation.EventManager;
import example.ViewService;
import org.jetbrains.annotations.NotNull;

public class MyToolWindowListener implements ToolWindowManagerListener {
    private final Project project;
    private final ViewService viewService;

    public MyToolWindowListener(Project project) {
        this.project = project;
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager, @NotNull ToolWindowManagerEventType changeType) {
        if (changeType.equals(ToolWindowManagerEventType.RegisterToolWindow)) {
            System.out.println("RegisterToolWindow");
            viewService.eventToolWindowReady();
        }

//        System.out.println(changeType);
//        if (changeType.equals(ToolWindowManagerEventType.ActivateToolWindow)) {
//            System.out.println("ActivateToolWindow");
//            viewService.eventActivateToolWindow();
//        }
    }
}
