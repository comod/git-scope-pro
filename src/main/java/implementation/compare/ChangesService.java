package implementation.compare;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.actions.GitCompareWithBranchAction;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import system.Defs;

import java.util.*;
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
                Collection<Change> _changes = new ArrayList<>();
                git.getRepositories().forEach(repo -> {
                    String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

//                    Collection<Change> changesPerRepo = doCollectChanges(project, repo, branchToCompare);
                    Collection<Change> changesPerRepo = null;
                    changesPerRepo = doCollectChanges(project, repo, branchToCompare);


                    // Simple "merge" logic
                    for (Change change : changesPerRepo) {
//                        //System.out.println(change);
                        if (!_changes.contains(change)) {
                            _changes.add(change);
                        }
                    }
                });
                changes = _changes;
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

//    private void merge(Collection<Change> firstChanges, Collection<Change> secondChanges) {
//
//        for (Change change : secondChanges) {
//            if (!firstChanges.contains(change)) {
//                firstChanges.add(change);
//            }
//        }
//
//    }

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

    @NotNull
    public Collection<Change> getChangesByHistory(Project project, GitRepository repo, String branchToCompare) throws VcsException {
//        GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(project.getBaseDir());
//        if (repository == null) {
//            throw new IllegalStateException("Git repository not found");
//        }
//
//        branchToCompare = "main..feature/add-sorting";
        List<GitCommit> commits = GitHistoryUtils.history(project, repo.getRoot(), branchToCompare);

        Map<FilePath, Change> changeMap = new HashMap<>();
        for (GitCommit commit : commits) {
            System.out.println(commit);
            for (Change change : commit.getChanges()) {
                FilePath path = ChangesUtil.getFilePath(change);
                changeMap.put(path, change);
            }
        }

        return new ArrayList<>(changeMap.values());
    }


    public Collection<Change> doCollectChanges(Project project, GitRepository repo, String branchToCompare) {
        //                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
        VirtualFile file = repo.getRoot();
//        //System.out.println("file " + file);
        Collection<Change> _changes = null;
        try {

            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();

            // Diff Changes
            if (branchToCompare.contains("..")) {
                _changes = getChangesByHistory(project, repo, branchToCompare);
            } else {
                _changes = getDiffChanges(project, file, branchToCompare);
            }
//            //System.out.println("diffChanges (repo: " + repo + ") changes: " + _changes);

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
//            //System.out.println("EEE Exception while collecting _changes " + e.getMessage());
            // silent
        }
        return _changes;
    }

}
