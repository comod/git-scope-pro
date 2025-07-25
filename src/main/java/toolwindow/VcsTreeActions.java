package toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.projectView.ProjectView;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class VcsTreeActions {
    public static class ShowInProjectAction extends AnAction {
        public ShowInProjectAction() {
            super("Show in Project");
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
                Change change = changes[0];
                VirtualFile file = null;

                if (change.getAfterRevision() != null && change.getAfterRevision().getFile().getVirtualFile() != null) {
                    file = change.getAfterRevision().getFile().getVirtualFile();
                } else if (change.getBeforeRevision() != null && change.getBeforeRevision().getFile().getVirtualFile() != null) {
                    file = change.getBeforeRevision().getFile().getVirtualFile();
                }

                if (file != null) {
                    ProjectView.getInstance(project).select(null, file, true);
                }
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabled(changes != null && changes.length > 0);
        }
    }

    public static class RollbackAction extends AnAction {
        public RollbackAction() {
            super("Rollback...");
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
            RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            e.getPresentation().setEnabled(changes != null && changes.length > 0);
        }
    }
}