package toolwindow.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import service.ToolWindowServiceInterface;
import service.ViewService;
import system.Defs;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * Action to rename a tab in the Git Scope tool window.
 * Registered in plugin.xml and works across all projects.
 */
public class RenameTabAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Content targetContent = getContentFromContextMenuEvent(e);
        if (targetContent == null) return;

        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        ToolWindow toolWindow = toolWindowService.getToolWindow();
        if (toolWindow == null) return;

        ContentManager contentManager = toolWindow.getContentManager();
        int index = contentManager.getIndexOfContent(targetContent);
        String currentName = targetContent.getDisplayName();

        // Don't allow renaming special tabs
        if (index == 0 || currentName.equals(ViewService.PLUS_TAB_LABEL)) {
            return;
        }

        String newName = Messages.showInputDialog(
                contentManager.getComponent(),
                "Enter new tab name:",
                "Rename Tab",
                Messages.getQuestionIcon(),
                currentName,
                null
        );

        if (newName != null && !newName.isEmpty()) {
            targetContent.setDisplayName(newName);

            // Update the model
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                viewService.onTabRenamed(index, newName);

                int modelIndex = viewService.getModelIndex(index);
                if (modelIndex >= 0 && modelIndex < viewService.getCollection().size()) {
                    MyModel model = viewService.getCollection().get(modelIndex);
                    toolWindowService.setupTabTooltip(model);
                }
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

            // Don't allow renaming special tabs (HEAD tab or PLUS tab)
            boolean enabled = index > 0 && !ViewService.PLUS_TAB_LABEL.equals(currentName);
            e.getPresentation().setEnabledAndVisible(enabled);
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
