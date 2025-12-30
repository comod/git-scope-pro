package implementation.compare;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import git4idea.GitRevisionNumber;
import git4idea.actions.GitCompareWithRefAction;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import system.Defs;
import utils.GitCommitReflection;
import utils.GitUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChangesService extends GitCompareWithRefAction {
    private static final Logger LOG = Defs.getLogger(ChangesService.class);

    public interface ErrorStateMarker {}

    public static class ErrorStateList extends AbstractList<Change> implements ErrorStateMarker {
        @Override public Change get(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int size() { return 0; }
        @Override public String toString() { return "ERROR_STATE_SENTINEL"; }
        @Override public boolean equals(Object o) { return o instanceof ErrorStateList; }
    }

    public static final Collection<Change> ERROR_STATE = new ErrorStateList();

    /**
     * Container for both merged changes and local changes towards HEAD.
     *
     * @param mergedChanges Scope changes + local changes
     * @param localChanges  Local changes towards HEAD only
     */
        public record ChangesResult(Collection<Change> mergedChanges, Collection<Change> localChanges) {
    }
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
            branchToCompare = targetBranchByRepo.value().get(repo.toString());
        }
        if (branchToCompare == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        }
        return branchToCompare;
    }

    // Cache for storing changes per repository
    private final Map<String, Collection<Change>> changesCache = new ConcurrentHashMap<>();

    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<ChangesResult> callBack, boolean checkFs) {
        // Capture the current project reference to ensure consistency
        final Project currentProject = this.project;
        final GitService currentGitService = this.git;

        task = new Task.Backgroundable(currentProject, "Collecting " + Defs.APPLICATION_NAME, true) {

            private ChangesResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Collection<Change> _changes = new ArrayList<>();
                Collection<Change> _localChanges = new ArrayList<>();
                List<String> errorRepos = new ArrayList<>();

                Collection<GitRepository> repositories = currentGitService.getRepositories();

                // Get all local changes from ChangeListManager once
                ChangeListManager changeListManager = ChangeListManager.getInstance(currentProject);
                Collection<Change> allLocalChanges = changeListManager.getAllChanges();

                repositories.forEach(repo -> {
                    try {
                        String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

                        // Use only repo path as cache key
                        String cacheKey = repo.getRoot().getPath();

                        Collection<Change> changesPerRepo = null;

                        if (!checkFs && changesCache.containsKey(cacheKey)) {
                            // Use cached changes if checkFs is false and cache exists
                            changesPerRepo = changesCache.get(cacheKey);
                        } else {
                            // Fetch fresh changes
                            changesPerRepo = doCollectChanges(currentProject, repo, branchToCompare);

                            // Cache the results (but don't cache error states)
                            if (!(changesPerRepo instanceof ErrorStateList)) {
                                changesCache.put(cacheKey, new ArrayList<>(changesPerRepo)); // Store a copy to avoid modification issues
                            }
                        }

                        if (changesPerRepo instanceof ErrorStateList) {
                            errorRepos.add(repo.getRoot().getPath());
                            return; // Skip this repo but continue with others
                        }

                        // Handle null case
                        if (changesPerRepo == null) {
                            changesPerRepo = new ArrayList<>();
                        }

                        // Merge changes into the collection
                        for (Change change : changesPerRepo) {
                            if (!_changes.contains(change)) {
                                _changes.add(change);
                            }
                        }

                        // Also collect local changes for this repository
                        String repoPath = repo.getRoot().getPath();
                        Collection<Change> repoLocalChanges = filterLocalChanges(allLocalChanges, repoPath, null);
                        for (Change change : repoLocalChanges) {
                            if (!_localChanges.contains(change)) {
                                _localChanges.add(change);
                            }
                        }
                    } catch (Exception e) {
                        // Catch any unexpected errors from individual repo processing
                        // This ensures one bad repo doesn't crash the entire operation
                        LOG.warn("Unexpected error processing repository " + repo.getRoot().getPath(), e);
                        errorRepos.add(repo.getRoot().getPath());
                    }
                });

                // Only return ERROR_STATE if ALL repositories failed
                if (!errorRepos.isEmpty() && _changes.isEmpty()) {
                    result = new ChangesResult(ERROR_STATE, new ArrayList<>());
                } else {
                    result = new ChangesResult(_changes, _localChanges);
                }
            }

            @Override
            public void onSuccess() {
                // Ensure result is accessed only on the UI thread to update the UI component
                ApplicationManager.getApplication().invokeLater(() -> {
                    // Double-check the project is still valid
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(this.result);
                    }
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(new ChangesResult(ERROR_STATE, new ArrayList<>()));
                    }
                });
            }
        };
        task.queue();
    }
    
    // Method to clear cache when needed
    public void clearCache() {
        changesCache.clear();
    }
    
    // Method to clear cache for specific repo
    public void clearCache(GitRepository repo) {
        String cacheKey = repo.getRoot().getPath();
        changesCache.remove(cacheKey);
    }

    /**
     * Filters local changes to include only those within the specified repository path.
     * Also optionally excludes changes that are already present in an existing collection.
     *
     * @param localChanges All local changes from the project
     * @param repoPath Repository root path to filter by
     * @param existingChanges Optional collection of existing changes to exclude duplicates (null to include all)
     * @return Filtered collection of changes
     */
    private Collection<Change> filterLocalChanges(Collection<Change> localChanges, String repoPath, Collection<Change> existingChanges) {
        Collection<Change> filtered = new ArrayList<>();

        for (Change change : localChanges) {
            VirtualFile changeFile = change.getVirtualFile();
            if (changeFile == null) {
                continue;
            }

            String changePath = changeFile.getPath();

            // Check if change belongs to this repository
            if (!changePath.startsWith(repoPath)) {
                continue;
            }

            // If existingChanges provided, skip duplicates
            if (existingChanges != null && !existingChanges.isEmpty()) {
                boolean isDuplicate = false;
                for (Change existing : existingChanges) {
                    VirtualFile existingFile = existing.getVirtualFile();
                    if (existingFile != null && changePath.equals(existingFile.getPath())) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (isDuplicate) {
                    continue;
                }
            }

            filtered.add(change);
        }

        return filtered;
    }

    @NotNull
    public Collection<Change> getChangesByHistory(Project project, GitRepository repo, String branchToCompare) throws VcsException {
        List<GitCommit> commits = GitHistoryUtils.history(project, repo.getRoot(), branchToCompare);
        Map<FilePath, Change> changeMap = new HashMap<>();
        for (GitCommit commit : commits) {
            // TODO: Reflection used to avoid triggering experimental API usage
            for (Change change : GitCommitReflection.getChanges(commit)) {
                FilePath path = ChangesUtil.getFilePath(change);
                changeMap.put(path, change);
            }
        }
        return new ArrayList<>(changeMap.values());
    }

    /**
     * Collects local changes for HEAD (uncommitted changes) filtered by repository.
     *
     * @param localChanges All local changes from the project
     * @param repo Repository to filter changes for
     * @return Collection of local changes within this repository
     */
    private Collection<Change> collectHeadChanges(Collection<Change> localChanges, GitRepository repo) {
        String repoPath = repo.getRoot().getPath();
        // For HEAD, we only want local changes within this repository (no existing changes to exclude)
        return filterLocalChanges(localChanges, repoPath, null);
    }

    public Collection<Change> doCollectChanges(Project project, GitRepository repo, String scopeRef) {
        VirtualFile file = repo.getRoot();
        Collection<Change> _changes = new ArrayList<>();
        try {
            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();

            // Special handling for HEAD - return local changes filtered by this repository
            if (scopeRef.equals(GitService.BRANCH_HEAD)) {
                return collectHeadChanges(localChanges, repo);
            }

            // Diff Changes
            if (scopeRef.contains("..")) {
                _changes = getChangesByHistory(project, repo, scopeRef);
            } else {
                GitReference gitReference;

                // First try to find matching branch or tag
                gitReference = repo.getBranches().findBranchByName(scopeRef);
                if (gitReference == null) {
                    // ... try a tag
                    gitReference = repo.getTagHolder().getTag(scopeRef);
                }

                GitRevisionNumber revisionNumber;
                if (gitReference == null) {
                    // Finally resort to try a generic reference (HEAD~2, <hash>, ...)
                    revisionNumber = GitUtil.resolveGitReference(repo, scopeRef);
                }
                else {
                    revisionNumber = new GitRevisionNumber(gitReference.getFullName());
                }

                if (revisionNumber != null) {
                    // We have a valid GitReference
                    _changes = GitUtil.getDiffChanges(repo, file, revisionNumber);
                }
                else {
                    // We do not have a valid GitReference => return null immediately (no point trying to add localChanges)
                    // null will be interpreted as invalid reference and displayed accordingly
                    return ERROR_STATE;
                }
            }

            // Add local changes that aren't already in the diff (filtered by repository and excluding duplicates)
            String repoPath = repo.getRoot().getPath();
            Collection<Change> additionalLocalChanges = filterLocalChanges(localChanges, repoPath, _changes);
            _changes.addAll(additionalLocalChanges);

        } catch (VcsException e) {
            // Log VCS errors (e.g., locked files, git command failures) but don't fail entirely
            LOG.warn("Error collecting changes for repository " + repo.getRoot().getPath() + ": " + e.getMessage());
        } catch (Exception e) {
            // Catch any other unexpected errors (e.g., file system issues)
            LOG.warn("Unexpected error collecting changes for repository " + repo.getRoot().getPath(), e);
        }
        return _changes;
    }

}