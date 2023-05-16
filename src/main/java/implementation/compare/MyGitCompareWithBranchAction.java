package implementation.compare;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.GitCompareWithBranchAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import system.Defs;

import java.util.Collection;
import java.util.function.Consumer;

public class MyGitCompareWithBranchAction extends GitCompareWithBranchAction {

    private GitRepository repo;
    private Task.Backgroundable task;

    public void collectChangesAndProcess(@NotNull Project project, @NotNull GitRepository repo, @NotNull String branchToCompare, Consumer<Collection<Change>> callBack) {

        VirtualFile file = repo.getRoot();

        task = new Task.Backgroundable(project, Defs.APPLICATION_NAME + ": Collecting Changes...", true) {

            private Collection<Change> changes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {

//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                try {

                    // Local Changes
                    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
                    Collection<Change> localChanges = changeListManager.getAllChanges();

                    // Diff Changes
                    this.changes = getDiffChanges(project, file, branchToCompare);

                    for (Change localChange : localChanges) {
                        VirtualFile localChangeVirtualFile = localChange.getVirtualFile();
                        if (localChangeVirtualFile == null) {
                            continue;
                        }

                        String localChangePath = localChangeVirtualFile.getPath();
                        // Add Local Change if not part of Diff Changes anyway

                        if (isLocalChangeOnly(localChangePath, changes)) {
                            changes.add(localChange);
                        }
                    }

                } catch (VcsException e) {
                    // silent
                }
            }

            @Override
            public void onSuccess() {
                callBack.accept(this.changes);
            }

        };
        task.queue();
    }

    private Boolean isLocalChangeOnly(String localChangePath, Collection<Change> changes) {

        if (changes == null) {
            return false;
        }

        for (Change change : changes) {
            VirtualFile vFile = change.getVirtualFile();
            if (vFile == null) {
                return false;
            }
            String changePath = change.getVirtualFile().getPath();

            if (localChangePath.equals(changePath)) {
                // we have already this file in our changes-list
                return false;
            }
        }

        return true;

    }
}
