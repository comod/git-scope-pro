package toolwindow.actions;

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
     * Helper class to hold validation result for update() and actionPerformed() methods
     */
    private static class UpdateContext {
        final ContentManager contentManager;
        final Content targetContent;
        final int currentIndex;
        final String tabName;

        UpdateContext(ContentManager contentManager, Content targetContent, int currentIndex, String tabName) {
            this.contentManager = contentManager;
            this.targetContent = targetContent;
            this.currentIndex = currentIndex;
            this.tabName = tabName;
        }
    }

    /**
     * Helper class to hold action context including project and validated context
     */
    private static class ActionContext {
        final Project project;
        final ContentManager contentManager;
        final Content targetContent;
        final int currentIndex;

        ActionContext(Project project, ContentManager contentManager, Content targetContent, int currentIndex) {
            this.project = project;
            this.contentManager = contentManager;
            this.targetContent = targetContent;
            this.currentIndex = currentIndex;
        }
    }

    /**
     * Shared validation logic for actionPerformed() methods.
     * Returns ActionContext if validation passes, null otherwise.
     */
    @Nullable
    private static ActionContext validateActionContext(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return null;

        // Get the right-clicked tab content
        Content targetContent = getContentFromContextMenuEvent(e);
        if (targetContent == null) return null;

        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        ContentManager contentManager = toolWindowService.getToolWindow().getContentManager();

        int currentIndex = contentManager.getIndexOfContent(targetContent);

        return new ActionContext(project, contentManager, targetContent, currentIndex);
    }

    /**
     * Shared validation logic for update() methods.
     * Returns UpdateContext if validation passes, null otherwise.
     */
    @Nullable
    private static UpdateContext validateUpdateContext(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return null;
        }

        // Check if this is our tool window
        ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
        if (toolWindow == null || !Defs.TOOL_WINDOW_NAME.equals(toolWindow.getId())) {
            return null;
        }

        // Get the right-clicked tab content
        Content targetContent = getContentFromContextMenuEvent(e);
        if (targetContent == null) {
            return null;
        }

        ContentManager contentManager = toolWindow.getContentManager();
        int currentIndex = contentManager.getIndexOfContent(targetContent);
        String tabName = targetContent.getTabName();

        return new UpdateContext(contentManager, targetContent, currentIndex, tabName);
    }

    /**
     * Action to move the current tab to the left.
     * Registered in plugin.xml and works across all projects.
     */
    public static class MoveTabLeft extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            ActionContext ctx = validateActionContext(e);
            if (ctx == null) return;

            int newIndex = ctx.currentIndex - 1;

            // Cannot move HEAD tab (index 0) or move before HEAD tab
            if (ctx.currentIndex <= 1 || newIndex < 1) {
                return;
            }

            // Cannot move + tab
            if (PLUS_TAB_LABEL.equals(ctx.targetContent.getTabName())) {
                return;
            }

            moveTab(ctx.project, ctx.contentManager, ctx.targetContent, ctx.currentIndex, newIndex);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            // By default, hide the action
            e.getPresentation().setEnabledAndVisible(false);

            UpdateContext ctx = validateUpdateContext(e);
            if (ctx == null) {
                return;
            }

            // Enable only if not HEAD tab (index 0), not + tab, and can move left (index > 1)
            boolean enabled = ctx.currentIndex > 1 && !PLUS_TAB_LABEL.equals(ctx.tabName);
            e.getPresentation().setEnabledAndVisible(enabled);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Action to move the current tab to the right.
     * Registered in plugin.xml and works across all projects.
     */
    public static class MoveTabRight extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            ActionContext ctx = validateActionContext(e);
            if (ctx == null) return;

            int newIndex = ctx.currentIndex + 1;
            int lastIndex = ctx.contentManager.getContentCount() - 1;

            // Cannot move HEAD tab (index 0) or move past + tab
            if (ctx.currentIndex == 0 || newIndex >= lastIndex) {
                return;
            }

            // Cannot move + tab
            if (PLUS_TAB_LABEL.equals(ctx.targetContent.getTabName())) {
                return;
            }

            moveTab(ctx.project, ctx.contentManager, ctx.targetContent, ctx.currentIndex, newIndex);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            // By default, hide the action
            e.getPresentation().setEnabledAndVisible(false);

            UpdateContext ctx = validateUpdateContext(e);
            if (ctx == null) {
                return;
            }

            int lastIndex = ctx.contentManager.getContentCount() - 1;

            // Enable only if not HEAD tab (index 0), not + tab, and can move right (not already at second-to-last position)
            boolean enabled = ctx.currentIndex > 0 && ctx.currentIndex < lastIndex - 1 && !PLUS_TAB_LABEL.equals(ctx.tabName);
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
