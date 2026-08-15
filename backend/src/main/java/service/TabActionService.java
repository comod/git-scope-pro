package service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.util.ArrayList;
import java.util.List;

/**
 * Backend-side tab operations addressed by tab index: rename, reset name, move.
 *
 * <p>The tool window, its ContentManager and the scope models are backend state, while the context
 * menu actions are registered on the frontend for both modes, so every tab change arrives here over
 * {@code UtilRpcApi}. This is the only place the tab rules live.
 *
 * <p>All operations validate the index themselves and no-op when it does not address a tab the
 * operation applies to: the frontend decides enablement from the tab strip alone and cannot see the
 * scope models, so its requests are treated as advisory.
 */
public class TabActionService {

    private static final Logger LOG = Defs.getLogger(TabActionService.class);

    private final Project project;

    public TabActionService(Project project) {
        this.project = project;
    }

    public void renameTab(int tabIndex, @NotNull String newName) {
        if (newName.isEmpty()) return;
        runOnEdt(() -> {
            ContentManager contentManager = getContentManager();
            if (contentManager == null) return;

            Content content = getRenameableContent(contentManager, tabIndex);
            if (content == null) {
                LOG.debug("renameTab: index " + tabIndex + " is not a renameable tab");
                return;
            }

            content.setDisplayName(newName);

            ViewService viewService = project.getService(ViewService.class);
            if (viewService == null) return;
            viewService.onTabRenamed(tabIndex, newName);

            MyModel model = getModelForTab(viewService, tabIndex);
            if (model != null) {
                project.getService(ToolWindowServiceInterface.class).setupTabTooltip(model);
            }
            publishCustomNamedTabs();
        });
    }

    public void resetTabName(int tabIndex) {
        runOnEdt(() -> {
            ContentManager contentManager = getContentManager();
            if (contentManager == null) return;

            Content content = getRenameableContent(contentManager, tabIndex);
            if (content == null) {
                LOG.debug("resetTabName: index " + tabIndex + " is not a renameable tab");
                return;
            }

            ViewService viewService = project.getService(ViewService.class);
            if (viewService == null) return;

            MyModel model = getModelForTab(viewService, tabIndex);
            if (model == null || model.getCustomTabName() == null || model.getCustomTabName().isEmpty()) {
                // Expected: the frontend cannot see the models, so it offers the action for every
                // renameable tab and relies on this check.
                LOG.debug("resetTabName: tab " + tabIndex + " has no custom name");
                return;
            }

            // Clearing the custom name restores the branch-based default name.
            model.setCustomTabName(null);
            viewService.save();

            TargetBranchService targetBranchService = project.getService(TargetBranchService.class);
            targetBranchService.getTargetBranchDisplayAsync(model.getTargetBranchMap(), branchName ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed()) return;
                        content.setDisplayName(branchName);
                        content.setDescription(null);
                    }));
            publishCustomNamedTabs();
        });
    }

    public void moveTab(int tabIndex, @NotNull rpc.TabMoveDirection direction) {
        runOnEdt(() -> {
            ContentManager contentManager = getContentManager();
            if (contentManager == null) return;

            int newIndex = direction == rpc.TabMoveDirection.LEFT ? tabIndex - 1 : tabIndex + 1;
            Content content = getMovableContent(contentManager, tabIndex, newIndex);
            if (content == null) {
                LOG.debug("moveTab: " + tabIndex + " -> " + newIndex + " is not a valid move");
                return;
            }

            LOG.debug("Moving tab from index " + tabIndex + " to " + newIndex);
            ViewService viewService = project.getService(ViewService.class);
            try {
                // Set the flag BEFORE moving so the content listener does not treat the
                // remove/add pair as a user-initiated tab change.
                if (viewService != null) {
                    viewService.setProcessingTabReorder(true);
                }

                contentManager.removeContent(content, false);
                contentManager.addContent(content, newIndex);
                contentManager.setSelectedContent(content, true);

                if (viewService != null) {
                    viewService.onTabReordered(tabIndex, newIndex);
                }
            } finally {
                if (viewService != null) {
                    viewService.setProcessingTabReorder(false);
                }
            }
            // Indices shifted, so the previously published set no longer addresses the same tabs.
            publishCustomNamedTabs();
        });
    }

    /**
     * Republishes which tabs carry a custom name, so the frontend can disable "Reset Tab Name" where
     * there is nothing to reset. Called after every operation that can change the answer, and once
     * when a frontend subscribes.
     */
    public void publishCustomNamedTabs() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            ContentManager contentManager = getContentManager();
            ViewService viewService = project.getService(ViewService.class);
            if (contentManager == null || viewService == null) return;

            List<Integer> indices = new ArrayList<>();
            for (int index = 1; index < contentManager.getContentCount(); index++) {
                if (!isRenameableTab(contentManager, index)) continue;
                MyModel model = getModelForTab(viewService, index);
                if (model != null && model.getCustomTabName() != null && !model.getCustomTabName().isEmpty()) {
                    indices.add(index);
                }
            }
            LOG.debug("publishCustomNamedTabs: " + indices);
            project.getService(rpc.UtilCommandService.class).setCustomNamedTabs(indices);
        });
    }

    // --- rules, shared by every caller ---

    /** Whether the tab at {@code index} may be renamed: not HEAD (index 0) and not the "+" tab. */
    public static boolean isRenameableTab(@NotNull ContentManager contentManager, int index) {
        if (index <= 0 || index >= contentManager.getContentCount()) return false;
        Content content = contentManager.getContent(index);
        return content != null && !Defs.PLUS_TAB_LABEL.equals(content.getTabName());
    }

    /** Whether the tab at {@code index} may move to {@code newIndex} (never onto HEAD or "+"). */
    public static boolean isMovableTab(@NotNull ContentManager contentManager, int index, int newIndex) {
        if (!isRenameableTab(contentManager, index)) return false;
        int lastIndex = contentManager.getContentCount() - 1;
        return newIndex >= 1 && newIndex < lastIndex;
    }

    private static @Nullable Content getRenameableContent(@NotNull ContentManager contentManager, int index) {
        return isRenameableTab(contentManager, index) ? contentManager.getContent(index) : null;
    }

    private static @Nullable Content getMovableContent(@NotNull ContentManager contentManager, int index, int newIndex) {
        return isMovableTab(contentManager, index, newIndex) ? contentManager.getContent(index) : null;
    }

    // --- helpers ---

    private @Nullable ContentManager getContentManager() {
        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        if (toolWindowService == null) return null;
        ToolWindow toolWindow = toolWindowService.getToolWindow();
        return toolWindow == null ? null : toolWindow.getContentManager();
    }

    private static @Nullable MyModel getModelForTab(@NotNull ViewService viewService, int tabIndex) {
        int modelIndex = viewService.getModelIndex(tabIndex);
        if (modelIndex < 0 || modelIndex >= viewService.getCollection().size()) return null;
        return viewService.getCollection().get(modelIndex);
    }

    private void runOnEdt(@NotNull Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            try {
                action.run();
            } catch (Exception e) {
                LOG.warn("Tab operation failed", e);
            }
        });
    }
}
