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
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import system.Defs;

import java.util.Collection;
import java.util.function.Consumer;

public class ChangesService extends GitCompareWithBranchAction {

    private final Project project;
    private GitRepository repo;
    private Task.Backgroundable task;

    private GitService git;

    public ChangesService(Project project) {
        this.project = project;
        this.git = project.getService(GitService.class);
    }

    @NotNull
    private static String getBranchToCompare(TargetBranchMap targetBranchByRepo, GitRepository repo) {
        String branchToCompare = null;
        if (targetBranchByRepo == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        } else {
            branchToCompare = targetBranchByRepo.getValue().get(repo.toString());
        }
//            String targetBranchByRepo = getTargetBranchByRepository(repo);
        if (branchToCompare == null) {
            // Notification.notify(Defs.NAME, "Choose a Branch");
//                toolWindowUI.showTargetBranchPopupAtToolWindow();
            branchToCompare = GitService.BRANCH_HEAD;
        }
        return branchToCompare;
    }

    //    public void collectChangesWithCallback(@NotNull Project project,
//                                           @NotNull GitRepository repo,
//                                           @NotNull String branchToCompare,
//                                           Consumer<Collection<Change>> callBack) {
//
    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<Collection<Change>> callBack) {
//        SwingUtilities.invokeLater(() -> {
//            Collection<Change> changes = doCollectChanges(project, repo, branchToCompare);
//            callBack.accept(changes);
//        });
        task = new Task.Backgroundable(this.project, Defs.APPLICATION_NAME + ": Collecting Changes...", true) {

            private Collection<Change> changes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                git.getRepositories().forEach(repo -> {
                    String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

                    System.out.println("compare repo: " + repo + ", branch: " + branchToCompare);

                    this.changes = doCollectChanges(project, repo, branchToCompare);
                });
            }

            @Override
            public void onSuccess() {
                callBack.accept(this.changes);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
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

    public Collection<Change> doCollectChanges(Project project, GitRepository repo, String branchToCompare) {
        //                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
        VirtualFile file = repo.getRoot();
        System.out.println("file " + file);
        Collection<Change> _changes = null;
        try {

            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();

            // Diff Changes
            _changes = getDiffChanges(project, file, branchToCompare);
            System.out.println("diffChanges (repo: " + repo + ") changes: " + _changes);

            for (Change localChange : localChanges) {
                VirtualFile localChangeVirtualFile = localChange.getVirtualFile();
                if (localChangeVirtualFile == null) {
                    continue;
                }

                String localChangePath = localChangeVirtualFile.getPath();
                // Add Local Change if not part of Diff Changes anyway

                if (isLocalChangeOnly(localChangePath, _changes)) {
                    _changes.add(localChange);
                }
            }

        } catch (VcsException e) {
            System.out.println("EEE Exception while collecting _changes " + e.getMessage());
            // silent
        }
        return _changes;
    }

}
