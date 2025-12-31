package toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import model.MyModel;
import service.TargetBranchService;
import service.ViewService;
import system.Defs;

import java.util.Map;

/**
 * Helper class for tab operations (tooltip setup, tab name changes).
 * Note: Tab context menu actions (rename, reset, move left/right) are now registered in plugin.xml
 * and implemented in separate action classes (RenameTabAction, ResetTabNameAction, TabMoveActions).
 */
public class TabOperations {
    private final Project project;

    public TabOperations(Project project) {
        this.project = project;
    }

    public void setupTabTooltip(MyModel model, Map<Content, ToolWindowView> contentToViewMap) {
        if (model == null || model.isHeadTab()) {
            return;
        }
        Content content = null;
        for (Map.Entry<Content, ToolWindowView> entry : contentToViewMap.entrySet()) {
            ToolWindowView view = entry.getValue();
            // We need to compare the models inside the ToolWindowView with our model
            if (view != null && view.getModel() == model) {
                content = entry.getKey();
                break;
            }
        }

        if (content == null) {
            return;
        }

        // Only set tooltip if this tab has a custom name
        String customName = model.getCustomTabName();
        if (customName != null && !customName.isEmpty()) {
            // Get the real branch info
            TargetBranchService targetBranchService = project.getService(TargetBranchService.class);
            Content finalContent = content;
            targetBranchService.getTargetBranchDisplayAsync(model.getTargetBranchMap(), branchInfo -> {
                if (branchInfo != null && !branchInfo.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        finalContent.setDescription(branchInfo);
                    });
                }
            });
        } else {
            // Clear tooltip if no custom name
            content.setDescription(null);
        }
    }

    public void changeTabName(String title, ContentManager contentManager) {
        Content selectedContent = contentManager.getSelectedContent();
        if (selectedContent != null) {
            String currentName = selectedContent.getDisplayName();
            // Only update if the name actually changed to prevent infinite loops
            if (!currentName.equals(title)) {
                selectedContent.setDisplayName(title);

                // Make sure the view service knows about the change so it can be persisted
                ViewService viewService = project.getService(ViewService.class);
                if (viewService != null) {
                    int index = contentManager.getIndexOfContent(selectedContent);
                    // Add a flag to indicate this is a UI-initiated rename
                    viewService.onTabRenamed(index, title);
                }
            }
        }
    }

    private ContentManager getContentManager() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(Defs.TOOL_WINDOW_NAME);
        assert toolWindow != null;
        return toolWindow.getContentManager();
    }
}
