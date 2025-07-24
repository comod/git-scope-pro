package implementation.compare;

import com.intellij.openapi.application.ApplicationManager;
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
import git4idea.GitReference;
import git4idea.actions.GitCompareWithRefAction;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import system.Defs;

import java.util.*;
import java.util.function.Consumer;

public class ChangesService extends GitCompareWithRefAction {
    public interface ErrorStateMarker {}
    public static class ErrorStateList extends AbstractList<Change> implements ErrorStateMarker {
        @Override public Change get(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int size() { return 0; }
        @Override public String toString() { return "ERROR_STATE_SENTINEL"; }
        @Override public boolean equals(Object o) { return o instanceof ErrorStateList; }
    }
    public static final Collection<Change> ERROR_STATE = new ErrorStateList();
    private final Project project;
    private final GitService git;
    private Task.Backgroundable task;

    public ChangesService(Project project) {
        this.project = project;
        this.git = project.getService(GitService.class);
    }

    @NotNull
    private static String getBranchToCompare(TargetBranchMap targetBranchByRepo, GitRepository repo) {
        String branchToCompare;
        if (targetBranchByRepo == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        } else {
            branchToCompare = targetBranchByRepo.getValue().get(repo.toString());
        }
        if (branchToCompare == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        }
        return branchToCompare;
    }


    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<Collection<Change>> callBack) {
        // Capture the current project reference to ensure consistency
        final Project currentProject = this.project;
        final GitService currentGitService = this.git;

        task = new Task.Backgroundable(currentProject, "Collecting " + Defs.APPLICATION_NAME, true) {

            private Collection<Change> changes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Collection<Change> _changes = new ArrayList<>();
                List<String> errorRepos = new ArrayList<>(); // Track problematic repos instead of failing entirely

                // IMPORTANT: Use the captured project and git service to ensure we're working with the right context
                Collection<GitRepository> repositories = currentGitService.getRepositories();

                repositories.forEach(repo -> {
                    String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

                    Collection<Change> changesPerRepo = null;
                    // Make sure to pass the correct project reference
                    changesPerRepo = doCollectChanges(currentProject, repo, branchToCompare);

                    if (changesPerRepo instanceof ErrorStateList) {
                        errorRepos.add(repo.getRoot().getPath());
                        return; // Skip this repo but continue with others
                    }

                    // Handle null case
                    if (changesPerRepo == null) {
                        changesPerRepo = new ArrayList<>();
                    }

                    // Simple "merge" logic
                    for (Change change : changesPerRepo) {
                        if (!_changes.contains(change)) {
                            _changes.add(change);
                        }
                    }
                });

                // Only return ERROR_STATE if ALL repositories failed
                if (!errorRepos.isEmpty() && _changes.isEmpty()) {
                    changes = ERROR_STATE;
                } else {
                    changes = _changes;
                }
            }

            @Override
            public void onSuccess() {
                // Ensure `changes` is accessed only on the UI thread to update the UI component
                ApplicationManager.getApplication().invokeLater(() -> {
                    // Double-check the project is still valid
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(this.changes);
                    }
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(ERROR_STATE);
                    }
                });
            }
        };
        task.queue();
    }


    private Boolean isLocalChangeOnly(String localChangePath, Collection<Change> changes) {

        if (changes == null || changes.isEmpty()) {
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
        List<GitCommit> commits = GitHistoryUtils.history(project, repo.getRoot(), branchToCompare);
        Map<FilePath, Change> changeMap = new HashMap<>();
        for (GitCommit commit : commits) {
            for (Change change : commit.getChanges()) {
                FilePath path = ChangesUtil.getFilePath(change);
                changeMap.put(path, change);
            }
        }
        return new ArrayList<>(changeMap.values());
    }

    public Collection<Change> doCollectChanges(Project project, GitRepository repo, String branchToCompare) {
        VirtualFile file = repo.getRoot();
        Collection<Change> _changes = new ArrayList<>();
        try {
            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();

            // Special handling for HEAD - just return local changes
            if (branchToCompare.equals(GitService.BRANCH_HEAD)) {
                _changes.addAll(localChanges);
                return _changes;
            }

            // Diff Changes
            if (branchToCompare.contains("..")) {
                _changes = getChangesByHistory(project, repo, branchToCompare);
            } else {
                GitReference gitReference;

                // First try to find matching branch
                gitReference = repo.getBranches().findBranchByName(branchToCompare);

                if (gitReference == null) {
                    // Then try a tag
                    gitReference = repo.getTagHolder().getTag(branchToCompare);
                }
                if (gitReference == null) {
                    // Finally resort to try a generic reference (HEAD~2, <hash>, ...)
                    gitReference = utils.GitUtil.resolveGitReference(repo, branchToCompare);
                }

                if (gitReference != null) {
                    // We have a valid GitReference
                    _changes = getDiffChanges(repo, file, gitReference);
                }
                else {
                    // We do not have a valid GitReference => return null immediately (no point trying to add localChanges)
                    // null will be interpreted as invalid reference and displayed accordingly
                    return ERROR_STATE;
                }
            }

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

        } catch (VcsException ignored) {
        }
        return _changes;
    }

}