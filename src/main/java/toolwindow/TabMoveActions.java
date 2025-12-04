package toolwindow;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.ToolWindowServiceInterface;
import service.ViewService;
import system.Defs;

import java.awt.*;
import java.lang.reflect.Field;

import static service.ViewService.PLUS_TAB_LABEL;

/**
 * Actions to move tabs left and right in the Git Scope tool window.
 */
public class TabMoveActions {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(TabMoveActions.class);

    /**
     * Gets the Content that was right-clicked in a context menu event
     */
    @Nullable
    private static Content getContentFromContextMenuEvent(AnActionEvent e) {
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

    /**
     * Action to move the current tab to the left
     */
    public static class MoveTabLeft extends AnAction {
        public MoveTabLeft() {
            super("Move Tab Left");
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            if (project == null) return;

            // Get the right-clicked tab content
            Content targetContent = getContentFromContextMenuEvent(e);
            if (targetContent == null) return;

            ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
            ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();

            int currentIndex = contentManager.getIndexOfContent(targetContent);
            int newIndex = currentIndex - 1;

            // Cannot move HEAD tab (index 0) or move before HEAD tab
            if (currentIndex <= 1 || newIndex < 1) {
                return;
            }

            // Cannot move + tab
            if (PLUS_TAB_LABEL.equals(targetContent.getTabName())) {
                return;
            }

            moveTab(project, contentManager, targetContent, currentIndex, newIndex);
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

            // Get the right-clicked tab content
            Content targetContent = getContentFromContextMenuEvent(e);
            if (targetContent == null) {
                return;
            }

            ContentManager contentManager = toolWindow.getContentManager();
            int currentIndex = contentManager.getIndexOfContent(targetContent);
            String tabName = targetContent.getTabName();

            // Enable only if not HEAD tab (index 0), not + tab, and can move left (index > 1)
            boolean enabled = currentIndex > 1 && !PLUS_TAB_LABEL.equals(tabName);
            e.getPresentation().setEnabledAndVisible(enabled);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Action to move the current tab to the right
     */
    public static class MoveTabRight extends AnAction {
        public MoveTabRight() {
            super("Move Tab Right");
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            if (project == null) return;

            // Get the right-clicked tab content
            Content targetContent = getContentFromContextMenuEvent(e);
            if (targetContent == null) return;

            ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
            ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();

            int currentIndex = contentManager.getIndexOfContent(targetContent);
            int newIndex = currentIndex + 1;
            int lastIndex = contentManager.getContentCount() - 1;

            // Cannot move HEAD tab (index 0) or move past + tab
            if (currentIndex == 0 || newIndex >= lastIndex) {
                return;
            }

            // Cannot move + tab
            if (PLUS_TAB_LABEL.equals(targetContent.getTabName())) {
                return;
            }

            moveTab(project, contentManager, targetContent, currentIndex, newIndex);
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

            // Get the right-clicked tab content
            Content targetContent = getContentFromContextMenuEvent(e);
            if (targetContent == null) {
                return;
            }

            ContentManager contentManager = toolWindow.getContentManager();
            int currentIndex = contentManager.getIndexOfContent(targetContent);
            int lastIndex = contentManager.getContentCount() - 1;
            String tabName = targetContent.getTabName();

            // Enable only if not HEAD tab (index 0), not + tab, and can move right (not already at second-to-last position)
            boolean enabled = currentIndex > 0 && currentIndex < lastIndex - 1 && !PLUS_TAB_LABEL.equals(tabName);
            e.getPresentation().setEnabledAndVisible(enabled);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Helper method to move a tab from one position to another
     */
    private static void moveTab(Project project, ContentManager contentManager, Content content, int oldIndex, int newIndex) {
        ViewService viewService = null;
        try {
            LOG.info("Moving tab from index " + oldIndex + " to " + newIndex);

            // Additional validation to prevent moving + tab or moving to + tab position
            int lastIndex = contentManager.getContentCount() - 1;

            // Cannot move HEAD tab (index 0)
            if (oldIndex == 0) {
                LOG.warn("Cannot move HEAD tab");
                return;
            }

            // Cannot move + tab (last index)
            if (oldIndex == lastIndex || PLUS_TAB_LABEL.equals(content.getTabName())) {
                LOG.warn("Cannot move + tab");
                return;
            }

            // Cannot move to position 0 (before HEAD) or to last position (where + tab is)
            if (newIndex < 1 || newIndex >= lastIndex) {
                LOG.warn("Invalid target index: " + newIndex);
                return;
            }

            // Verify the + tab is still at the last position
            Content lastContent = contentManager.getContent(lastIndex);
            if (lastContent == null || !PLUS_TAB_LABEL.equals(lastContent.getTabName())) {
                LOG.error("+ tab is not at expected position!");
                return;
            }

            // IMPORTANT: Set the flag BEFORE moving tabs to prevent listener interference
            viewService = project.getService(ViewService.class);
            if (viewService != null) {
                viewService.setProcessingTabReorder(true);
            }

            // Remove content from old position
            contentManager.removeContent(content, false);

            // Add content at new position
            contentManager.addContent(content, newIndex);

            // Select the moved tab
            contentManager.setSelectedContent(content, true);

            // Now rebuild collection and save
            if (viewService != null) {
                viewService.onTabReordered(oldIndex, newIndex);
            }

            LOG.info("Tab moved successfully");
        } catch (Exception e) {
            LOG.error("Error moving tab: " + e.getMessage(), e);
        } finally {
            // Always clear the flag
            if (viewService != null) {
                viewService.setProcessingTabReorder(false);
            }
        }
    }
}
