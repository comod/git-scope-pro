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
import java.util.concurrent.atomic.AtomicReference;
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
        if (branchToCompare == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        }
        return branchToCompare;
    }

    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<Collection<Change>> callBack) {
        task = new Task.Backgroundable(this.project, Defs.APPLICATION_NAME + ": Collecting Changes...", true) {

            private Collection<Change> changes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Collection<Change> _changes = new ArrayList<>();
                AtomicReference<Collection<Change>> fatalChange = new AtomicReference<>();
                git.getRepositories().forEach(repo -> {
                    String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

                    Collection<Change> changesPerRepo = null;
                    changesPerRepo = doCollectChanges(project, repo, branchToCompare);

                    if (changesPerRepo instanceof ErrorStateList)
                    {
                        fatalChange.set(changesPerRepo);
                    }
                    // Simple "merge" logic
                    for (Change change : changesPerRepo) {
                        if (!_changes.contains(change)) {
                            _changes.add(change);
                        }
                    }
                });
                if (fatalChange.get() != null) {
                    changes = fatalChange.get();
                } else {
                    changes = _changes;
                }
            }

            @Override
            public void onSuccess() {
                // Ensure `changes` is accessed only on the UI thread to update the UI component
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (callBack != null) {
                        callBack.accept(this.changes);
                    }
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                callBack.accept(this.changes);
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
        Collection<Change> _changes = null;
        try {
            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();

            // Diff Changes
            if (branchToCompare.contains("..")) {
                _changes = getChangesByHistory(project, repo, branchToCompare);
            } else {
                GitReference gitReference;

                if (branchToCompare.equals(GitService.BRANCH_HEAD)) {
                    gitReference = repo.getCurrentBranch();
                } else {
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
