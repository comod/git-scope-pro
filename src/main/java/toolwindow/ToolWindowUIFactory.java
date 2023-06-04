package toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class ToolWindowUIFactory implements ToolWindowFactory {

    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Even on Start, or on open toolWindow
    }
}