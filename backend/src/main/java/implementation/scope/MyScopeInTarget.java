package implementation.scope;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import service.ToolWindowServiceInterface;
import system.Defs;

public class MyScopeInTarget implements SelectInTarget {
    private final Project project;

    public MyScopeInTarget(Project project) {
        this.project = project;
    }

    @Override
    public boolean canSelect(SelectInContext context) {
        // Return true if your Git Scope window can select the file in the given context.
        return context.getVirtualFile() != null;
    }

    @Override
    public void selectIn(SelectInContext context, boolean requestFocus) {
        VirtualFile file = context.getVirtualFile();

        // Show your Git Scope window and select the file.
        showAndSelectFile(project, file, requestFocus);
    }

    private void showAndSelectFile(Project project, VirtualFile file, boolean requestFocus) {
        // First, make sure the tool window is visible
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(Defs.TOOL_WINDOW_NAME);

        if (toolWindow != null) {
            // Show the tool window
            toolWindow.show();

            if (requestFocus) {
                toolWindow.activate(null);
            }

            // Get the VcsTree and select the file
            ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
            if (toolWindowService != null) {
                toolWindowService.selectFile(file);
            }
        }
    }


    @Override
    public String getToolWindowId() {
        return Defs.TOOL_WINDOW_NAME;
    }

    @Override
    public String toString() {
        return Defs.APPLICATION_NAME;
    }

    @Override
    public String getMinorViewId() {
        return null;
    }

    @Override
    public float getWeight() {
        return 2; // lower comes first
    }
}