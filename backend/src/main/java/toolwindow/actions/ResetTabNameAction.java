package toolwindow.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import service.TargetBranchService;
import service.ViewService;
import system.Defs;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * Action to reset a tab name to its original branch-based name.
 * Registered in plugin.xml and works across all projects.
 */
public class ResetTabNameAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Content targetContent = getContentFromContextMenuEvent(e);
        if (targetContent == null) return;

        ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
        if (toolWindow == null || !Defs.TOOL_WINDOW_NAME.equals(toolWindow.getId())) {
            return;
        }

        ContentManager contentManager = toolWindow.getContentManager();
        int index = contentManager.getIndexOfContent(targetContent);

        // Don't allow resetting special tabs
        if (index == 0 || targetContent.getDisplayName().equals(ViewService.PLUS_TAB_LABEL)) {
            return;
        }

        ViewService viewService = project.getService(ViewService.class);
        if (viewService != null) {
            int modelIndex = viewService.getModelIndex(index);
            if (modelIndex >= 0 && modelIndex < viewService.getCollection().size()) {
                MyModel model = viewService.getCollection().get(modelIndex);

                // Clear the custom name - this effectively resets to the default branch-based name
                model.setCustomTabName(null);

                // Save the change
                viewService.save();

                // Update the UI with the branch-based name
                TargetBranchService targetBranchService = project.getService(TargetBranchService.class);
                targetBranchService.getTargetBranchDisplayAsync(model.getTargetBranchMap(), branchName -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Update the tab name in the UI
                        targetContent.setDisplayName(branchName);
                        // Clear the tooltip
                        targetContent.setDescription(null);
                    });
                });
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // By default, hide the action
        e.getPresentation().setEnabledAndVisible(false);

        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Check if this is our tool window
        ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
        if (toolWindow == null || !Defs.TOOL_WINDOW_NAME.equals(toolWindow.getId())) {
            return;
        }

        // Get the content that was right-clicked
        Content targetContent = getContentFromContextMenuEvent(e);
        if (targetContent != null) {
            ContentManager contentManager = toolWindow.getContentManager();
            int index = contentManager.getIndexOfContent(targetContent);
            String currentName = targetContent.getDisplayName();

            // Enable only for non-special tabs that have a custom name
            boolean isSpecialTab = index == 0 || ViewService.PLUS_TAB_LABEL.equals(currentName);
            if (!isSpecialTab) {
                // Check if this tab has a custom name
                ViewService viewService = project.getService(ViewService.class);
                int modelIndex = viewService.getModelIndex(index);
                if (modelIndex >= 0 && modelIndex < viewService.getCollection().size()) {
                    MyModel model = viewService.getCollection().get(modelIndex);
                    boolean hasCustomName = model.getCustomTabName() != null && !model.getCustomTabName().isEmpty();
                    e.getPresentation().setEnabledAndVisible(hasCustomName);
                }
            }
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * Gets the Content that was right-clicked in a context menu event
     */
    private Content getContentFromContextMenuEvent(AnActionEvent e) {
        Component contextComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (contextComponent == null) {
            return null;
        }
        try {
            Field myContentField = contextComponent.getClass().getDeclaredField("myContent");
            myContentField.setAccessible(true);
            Object myContentObject = myContentField.get(contextComponent);
            if (myContentObject instanceof Content) {
                return (Content) myContentObject;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return null;
    }
}
