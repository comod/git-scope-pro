package toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.projectView.ProjectView;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.ToolWindowServiceInterface;
import service.ViewService;
import state.State;
import utils.CustomRollback;

import java.util.Arrays;
import java.util.List;

public class VcsTreeActions {
    public static class ShowInProjectAction extends AnAction {
        public ShowInProjectAction() {
            super("Show in Project", "Locate file in the Project view", AllIcons.General.Locate);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            Change[] changes = e.getData(VcsDataKeys.CHANGES);

            if (project != null && changes != null && changes.length > 0) {
                VirtualFile file = null;

                // Check if the selected tree node is a directory
                com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase browser =
                        e.getData(com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase.DATA_KEY);
                if (browser != null) {
                    Object node = browser.getViewer().getLastSelectedPathComponent();
                    if (node instanceof com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode<?> cbn) {
                        Object userObject = cbn.getUserObject();
                        if (userObject instanceof com.intellij.openapi.vcs.FilePath fp && fp.isDirectory() && fp.getVirtualFile() != null) {
                            file = fp.getVirtualFile();
                        }
                    }
                }

                if (file == null) {
                    file = getFileFromChange(changes[0]);
                }

                if (file != null) {
                    project.getService(rpc.UtilCommandService.class).selectInProject(file.getUrl());
                }
            }
        }

        private VirtualFile getFileFromChange(Change change) {
            if (change.getAfterRevision() != null && change.getAfterRevision().getFile().getVirtualFile() != null) {
                return change.getAfterRevision().getFile().getVirtualFile();
            } else if (change.getBeforeRevision() != null && change.getBeforeRevision().getFile().getVirtualFile() != null) {
                return change.getBeforeRevision().getFile().getVirtualFile();
            }
            return null;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabled(changes != null && changes.length > 0);
        }
    }

    public static class RollbackAction extends AnAction {
        public RollbackAction() {
            super("Rollback...", "Rollback selected changes", AllIcons.Actions.Rollback);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getData(CommonDataKeys.PROJECT);
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            if (project == null || changes == null || changes.length == 0) return;
            CustomRollback rollback = new CustomRollback();

            ViewService viewService = project.getService(ViewService.class);
            MyModel currentModel = viewService.getCurrent();
            rollback.rollbackChanges(project, changes, currentModel.getScopeRef());
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabled(changes != null && changes.length > 0);
        }
    }

    public static class CreatePatchAction extends AnAction {
        public CreatePatchAction() {
            super("Create Patch...", "Create a patch file from selected changes", AllIcons.Vcs.Patch);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            if (project == null || changes == null || changes.length == 0) return;
            CreatePatchFromChangesAction.createPatch(project, null, Arrays.asList(changes));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabledAndVisible(changes != null && changes.length > 0);
        }
    }

    public static class CopyAsPatchAction extends AnAction {
        public CopyAsPatchAction() {
            super("Copy as Patch to Clipboard", "Copy selected changes as a patch to the clipboard", null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            if (project == null || changes == null || changes.length == 0) return;
            CreatePatchFromChangesAction.createPatch(project, null, Arrays.asList(changes), true);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabledAndVisible(changes != null && changes.length > 0);
        }
    }

    /**
     * Wraps a platform action group (e.g. "Group By") so it right-aligns in the toolbar next to
     * our own {@link RightAlignedToolbarAction} toggles, without reimplementing it.
     */
    public static class RightAlignedGroup extends ActionGroupWrapper implements RightAlignedToolbarAction {
        public RightAlignedGroup(@NotNull ActionGroup delegate) {
            super(delegate);
        }

        /**
         * {@code ChangesBrowserBase.init()} walks the whole toolbar group with
         * {@code DiffUtil.recursiveRegisterShortcutSet} to register shortcuts, which calls
         * {@code getChildren(null)} on any {@link ActionGroup} it doesn't specifically recognize --
         * including this wrapper. Forwarding a null event straight to the delegate (a
         * {@link DefaultActionGroup}) trips its "do not call getChildren(null)" assertion, so route
         * around it the same way {@code DiffUtil} itself does for a plain {@link DefaultActionGroup}.
         */
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            if (e == null && getDelegate() instanceof DefaultActionGroup delegateGroup) {
                return delegateGroup.getChildren(ActionManager.getInstance());
            }
            return super.getChildren(e);
        }
    }

    /** Presentation toggles (untracked/deleted files, "Group By") right-align via {@link RightAlignedToolbarAction},
     * away from the action icons (rollback, create patch, ...) on the left. */
    public static class ShowUntrackedFilesAction extends ToggleAction implements RightAlignedToolbarAction {
        public ShowUntrackedFilesAction() {
            super("Show Untracked Files", "Show untracked (unversioned) files in this project", AllIcons.Vcs.ShowUnversionedFiles);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            if (project == null) return false;
            return Boolean.TRUE.equals(project.getService(State.class).getShowUntrackedFiles());
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            Project project = e.getProject();
            if (project == null) return;
            project.getService(State.class).setShowUntrackedFiles(state);
            refreshChanges(project);
        }
    }

    public static class ShowDeletedFilesAction extends ToggleAction implements RightAlignedToolbarAction {
        public ShowDeletedFilesAction() {
            super("Show Deleted Files", "Show locally deleted files in this project", AllIcons.Actions.Close);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            if (project == null) return false;
            return Boolean.TRUE.equals(project.getService(State.class).getShowDeletedFiles());
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            Project project = e.getProject();
            if (project == null) return;
            project.getService(State.class).setShowDeletedFiles(state);
            refreshChanges(project);
        }
    }

    private static void refreshChanges(@NotNull Project project) {
        ViewService viewService = project.getService(ViewService.class);
        if (viewService != null && !viewService.isDisposed()) {
            viewService.collectChanges(true);
        }
    }

    public static class SelectOpenedFileAction extends AnAction {
        public SelectOpenedFileAction() {
            super("Select Opened File", "Select the file currently open in the editor", AllIcons.General.Locate);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            // Keep presentation stable - no modifications needed
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getData(CommonDataKeys.PROJECT);
            if (project == null) return;
            ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);

            FileEditorManager fem = FileEditorManager.getInstance(project);
            VirtualFile file = fem.getSelectedFiles().length > 0 ? fem.getSelectedFiles()[0] : null;
            if (file != null) {
                toolWindowService.selectFile(file);
            }
        }
    }
}