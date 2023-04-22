package implementation.compare;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
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

    public void collectChangesAndProcess(
            @NotNull Project project,
            @NotNull GitRepository repo,
            @NotNull String branchToCompare,
            Consumer<Collection<Change>> callBack
    ) {

        VirtualFile file = repo.getRoot();

        task = new Task.Backgroundable(project, Defs.APPLICATION_NAME + ": Collecting Changes...", true) {

            private Collection<Change> changes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {

                    this.changes = getDiffChanges(project, file, branchToCompare);

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
}
