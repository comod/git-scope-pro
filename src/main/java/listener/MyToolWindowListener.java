package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
//import implementation.EventManager;
import example.ViewService;
import org.jetbrains.annotations.NotNull;
import service.ToolWindowServiceInterface;

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
            System.out.println("MyToolWindowListener registered");
//            EventManager manager = project.getService(EventManager.class);
            System.out.println("initToolWindow");
            viewService.addTab();
//            ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
//            toolWindowService.addTab();
        }

//        if (changeType.equals(ToolWindowManagerEventType.MovedOrResized)) {
//            System.out.println("=====stateChanged MovedOrResized");
//        }
    }
}
