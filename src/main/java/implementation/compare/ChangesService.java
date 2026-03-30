package implementation.compare;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import utils.PlatformApiReflection;
import utils.GitUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ChangesService extends GitCompareWithRefAction implements Disposable {
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
     * Container for merged changes, scope changes, and local changes towards HEAD.
     *
     * @param mergedChanges Scope changes + local changes (union of scopeChanges and localChanges)
     * @param scopeChanges  Scope changes only (from target branch comparison)
     * @param localChanges  Local changes towards HEAD only
     */
        public record ChangesResult(Collection<Change> mergedChanges, Collection<Change> scopeChanges, Collection<Change> localChanges) {
    }
    private final Project project;
    private final GitService git;
    private Task.Backgroundable task;
    private final AtomicBoolean disposing = new AtomicBoolean(false);

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

    // Cache for storing changes per repository (stores RepoChangesResult to preserve scope/local separation)
    private final Map<String, RepoChangesResult> changesCache = new ConcurrentHashMap<>();

    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<ChangesResult> callBack, boolean checkFs) {
        // Capture the current project reference to ensure consistency
        final Project currentProject = this.project;
        final GitService currentGitService = this.git;

        task = new Task.Backgroundable(currentProject, "Collecting " + Defs.APPLICATION_NAME, true) {

            private ChangesResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Early exit if disposing
                if (disposing.get() || indicator.isCanceled()) {
                    return;
                }

                Collection<Change> _changes = new ArrayList<>();
                Collection<Change> _scopeChanges = new ArrayList<>();
                Collection<Change> _localChanges = new ArrayList<>();
                List<String> errorRepos = new ArrayList<>();

                Collection<GitRepository> repositories = currentGitService.getRepositories();

                // Clear cache if checkFs is true (force fresh fetch)
                if (checkFs) {
                    changesCache.clear();
                }

                repositories.forEach(repo -> {
                    try {
                        String branchToCompare = getBranchToCompare(targetBranchByRepo, repo);

                        // Use repo path + target branch as cache key to ensure different branches don't share cache
                        String cacheKey = repo.getRoot().getPath() + "|" + branchToCompare;

                        RepoChangesResult repoResult;

                        if (!checkFs && changesCache.containsKey(cacheKey)) {
                            // Use cached result (includes merged, scope, and local changes)
                            repoResult = changesCache.get(cacheKey);
                        } else {
                            // Fetch fresh changes
                            repoResult = doCollectChanges(currentProject, repo, branchToCompare);

                            // Cache the complete result (but don't cache error states)
                            if (!(repoResult.mergedChanges() instanceof ErrorStateList)) {
                                // Create deep copies to avoid modification issues
                                RepoChangesResult cachedResult = new RepoChangesResult(
                                    new ArrayList<>(repoResult.mergedChanges()),
                                    new ArrayList<>(repoResult.scopeChanges()),
                                    new ArrayList<>(repoResult.localChanges())
                                );
                                changesCache.put(cacheKey, cachedResult);
                            }
                        }

                        if (repoResult.mergedChanges() instanceof ErrorStateList) {
                            errorRepos.add(repo.getRoot().getPath());
                            return; // Skip this repo but continue with others
                        }

                        // Merge merged changes into the collection
                        for (Change change : repoResult.mergedChanges()) {
                            if (!_changes.contains(change)) {
                                _changes.add(change);
                            }
                        }

                        // Merge scope changes into the collection
                        for (Change change : repoResult.scopeChanges()) {
                            if (!_scopeChanges.contains(change)) {
                                _scopeChanges.add(change);
                            }
                        }

                        // Merge local changes from the repo result into the collection
                        for (Change change : repoResult.localChanges()) {
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

                // Return ERROR_STATE if ANY repository had an invalid reference
                // Since target branch is per-repo, if the specified repo fails, the entire scope is invalid
                if (!errorRepos.isEmpty()) {
                    result = new ChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>());
                } else {
                    result = new ChangesResult(_changes, _scopeChanges, _localChanges);
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
                }, ModalityState.defaultModalityState(), __ -> disposing.get());
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(new ChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>()));
                    }
                }, ModalityState.defaultModalityState(), __ -> disposing.get());
            }
        };
        task.queue();
    }
    
    @Override
    public void dispose() {
        // Set disposing flag to prevent queued callbacks from executing
        disposing.set(true);

        // Clear cache to release memory
        clearCache();
    }

    // Method to clear cache when needed
    public void clearCache() {
        changesCache.clear();
    }

    // Method to clear cache for specific repo (clears all entries for this repo across all branches)
    public void clearCache(GitRepository repo) {
        String repoPath = repo.getRoot().getPath();
        // Remove all cache entries that start with this repo path
        changesCache.keySet().removeIf(key -> key.startsWith(repoPath + "|"));
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
            for (Change change : PlatformApiReflection.getCommitChanges(commit)) {
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

    /**
     * Result container for changes collection that separates scope, local, and merged changes.
     *
     * @param mergedChanges Scope changes + local changes (union)
     * @param scopeChanges Scope changes only (from target branch comparison)
     * @param localChanges Local changes only (towards HEAD, filtered by repository)
     */
    public record RepoChangesResult(Collection<Change> mergedChanges, Collection<Change> scopeChanges, Collection<Change> localChanges) {}

    public RepoChangesResult doCollectChanges(Project project, GitRepository repo, String scopeRef) {
        VirtualFile file = repo.getRoot();
        Collection<Change> scopeChanges = new ArrayList<>();
        Collection<Change> mergedChanges = new ArrayList<>();
        Collection<Change> repoLocalChanges;

        try {
            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = changeListManager.getAllChanges();
            String repoPath = repo.getRoot().getPath();

            // Filter local changes for this repository
            repoLocalChanges = filterLocalChanges(localChanges, repoPath, null);

            // Special handling for HEAD - return local changes only, no scope changes
            if (scopeRef.equals(GitService.BRANCH_HEAD)) {
                return new RepoChangesResult(new ArrayList<>(repoLocalChanges), new ArrayList<>(), repoLocalChanges);
            }

            // Diff Changes - these are the pure scope changes
            if (scopeRef.contains("..")) {
                scopeChanges = getChangesByHistory(project, repo, scopeRef);
            } else {
                GitReference gitReference;

                // First try to find matching branch or tag (skip for relative refs like HEAD~1)
                gitReference = repo.getBranches().findBranchByName(scopeRef);
                if (gitReference == null && !scopeRef.contains("~") && !scopeRef.contains("^")) {
                    // ... try a tag
                    gitReference = PlatformApiReflection.findTagByName(repo, scopeRef);
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
                    scopeChanges = GitUtil.getDiffChanges(repo, file, revisionNumber);
                    LOG.info("ChangesService - Repository: " + repoPath + ", Scope: " + scopeRef + ", scopeChanges count: " + scopeChanges.size());
                }
                else {
                    // We do not have a valid GitReference => return ERROR_STATE
                    return new RepoChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>());
                }
            }

            // Log what we collected
            LOG.info("ChangesService - Repository: " + repoPath + ", localChanges count: " + repoLocalChanges.size());

            // Create merged changes: start with scope changes, then add local changes
            mergedChanges.addAll(scopeChanges);

            // Add local changes that aren't already in the scope changes (excluding duplicates)
            Collection<Change> additionalLocalChanges = filterLocalChanges(repoLocalChanges, repoPath, scopeChanges);
            LOG.info("ChangesService - Repository: " + repoPath + ", additionalLocalChanges count (after filtering): " + additionalLocalChanges.size());
            mergedChanges.addAll(additionalLocalChanges);

            LOG.info("ChangesService - Repository: " + repoPath + ", Final counts - scope: " + scopeChanges.size() + ", local: " + repoLocalChanges.size() + ", merged: " + mergedChanges.size());

        } catch (VcsException e) {
            // Log VCS errors (e.g., locked files, git command failures) but don't fail entirely
            LOG.warn("Error collecting changes for repository " + repo.getRoot().getPath() + ": " + e.getMessage());
            return new RepoChangesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        } catch (Exception e) {
            // Catch any other unexpected errors (e.g., file system issues)
            LOG.warn("Unexpected error collecting changes for repository " + repo.getRoot().getPath(), e);
            return new RepoChangesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        return new RepoChangesResult(mergedChanges, scopeChanges, repoLocalChanges);
    }

}