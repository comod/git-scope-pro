package toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import service.TargetBranchService;
import service.ToolWindowServiceInterface;
import service.ViewService;
import system.Defs;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Map;

public class TabRename {
    private final Project project;

    public TabRename(Project project) {
        this.project = project;
    }

    public void registerRenameTabAction() {
        // Create a rename action that will be added to the tab context menu
        AnAction renameAction = new AnAction("Rename Tab") {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Get the right-clicked tab, not the selected one
                Content targetContent = getContentFromContextMenuEvent(e);
                if (targetContent != null) {
                    renameTab(targetContent);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                // By default, hide the action
                e.getPresentation().setEnabledAndVisible(false);

                // Get the project from the action event
                Project eventProject = e.getProject();
                if (eventProject == null || !eventProject.equals(project)) {
                    return; // Not our project or no project
                }

                // Get the tool window directly from the event data
                ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);

                // Only proceed for our Git Scope tool window
                if (toolWindow != null && Defs.TOOL_WINDOW_NAME.equals(toolWindow.getId())) {
                    // Get the content that was right-clicked, not the selected one
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
            }
        };

        // Create a reset tab name action
        AnAction resetTabNameAction = new AnAction("Reset Tab Name") {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Get the right-clicked tab
                Content targetContent = getContentFromContextMenuEvent(e);
                if (targetContent != null) {
                    resetTabName(targetContent);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                // By default, hide the action
                e.getPresentation().setEnabledAndVisible(false);

                // Get the project from the action event
                Project eventProject = e.getProject();
                if (eventProject == null || !eventProject.equals(project)) {
                    return;
                }

                // Get the tool window from the event data
                ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);

                // Only proceed for our Git Scope tool window
                if (toolWindow != null && Defs.TOOL_WINDOW_NAME.equals(toolWindow.getId())) {
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
            }
        };

        // Get the action manager and register our actions
        ActionManager actionManager = ActionManager.getInstance();

        // Register the rename action
        String renameActionId = "GitScope.RenameTab";
        if (actionManager.getAction(renameActionId) == null) {
            actionManager.registerAction(renameActionId, renameAction);
        } else {
            actionManager.unregisterAction(renameActionId);
            actionManager.registerAction(renameActionId, renameAction);
        }

        // Register the reset action
        String resetActionId = "GitScope.ResetTabName";
        if (actionManager.getAction(resetActionId) == null) {
            actionManager.registerAction(resetActionId, resetTabNameAction);
        } else {
            actionManager.unregisterAction(resetActionId);
            actionManager.registerAction(resetActionId, resetTabNameAction);
        }

        // Add the actions to the ToolWindowContextMenu group
        DefaultActionGroup contextMenuGroup = (DefaultActionGroup) actionManager.getAction("ToolWindowContextMenu");
        if (contextMenuGroup != null) {
            // Remove any existing instances of our actions first to avoid duplicates
            AnAction[] actions = contextMenuGroup.getChildActionsOrStubs();
            for (AnAction action : actions) {
                if (action.getTemplateText() != null &&
                        (action.getTemplateText().equals("Rename Tab") || action.getTemplateText().equals("Reset Tab Name"))) {
                    contextMenuGroup.remove(action);
                }
            }

            // Add our actions to the group in the desired order:
            // 1. Rename Tab
            // 2. Reset Tab Name
            contextMenuGroup.add(resetTabNameAction, Constraints.FIRST);  // Added second, appears first from bottom
            contextMenuGroup.add(renameAction, Constraints.FIRST);        // Added first, appears first from top
        }
    }

    /**
     * Resets a tab name to its original branch-based name by clearing the custom name
     */
    public void resetTabName(Content content) {
        ContentManager contentManager = getContentManager();
        int index = contentManager.getIndexOfContent(content);

        // Don't allow resetting special tabs
        if (index == 0 || content.getDisplayName().equals(ViewService.PLUS_TAB_LABEL)) {
            return;
        }

        ViewService viewService = project.getService(ViewService.class);
        if (viewService != null) {
            int modelIndex = viewService.getModelIndex(index);
            if (modelIndex >= 0 && modelIndex < viewService.getCollection().size()) {
                MyModel model = viewService.getCollection().get(modelIndex);

                // Clear the custom name
                model.setCustomTabName(null);

                // Update the UI with the branch-based name
                TargetBranchService targetBranchService = project.getService(TargetBranchService.class);
                targetBranchService.getTargetBranchDisplayAsync(model.getTargetBranchMap(), branchName -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Update the tab name in the UI
                        content.setDisplayName(branchName);
                        // Clear the tooltip
                        content.setDescription(null);

                        // Notify the view service of the change
                        viewService.onTabRenamed(index, branchName);
                    });
                });
            }
        }
    }

    /**
     * Gets the Content that was right-clicked in a context menu event
     */
    private Content getContentFromContextMenuEvent(AnActionEvent e) {
        // Try to get the specific component that was clicked on
        Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (component == null) {
            return null;
        }
        Component contextComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        try {
            // Try to access the "myComponent" field using reflection
            assert contextComponent != null;
            Field myComponentField = contextComponent.getClass().getDeclaredField("myContent");
            myComponentField.setAccessible(true); // Make private field accessible

            Object myComponentObject = myComponentField.get(contextComponent);
            if (myComponentObject instanceof Content) {
                return (Content) myComponentObject;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return null;
    }

    public void renameTab(Content content) {
        ContentManager contentManager = getContentManager();
        int index = contentManager.getIndexOfContent(content);
        String currentName = content.getDisplayName();

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
            content.setDisplayName(newName);

            // Update the model
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                viewService.onTabRenamed(index, newName);

                int modelIndex = viewService.getModelIndex(index);
                if (modelIndex >= 0 && modelIndex < viewService.getCollection().size()) {
                    MyModel model = viewService.getCollection().get(modelIndex);
                    ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
                    toolWindowService.setupTabTooltip(model);
                }
            }
        }
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