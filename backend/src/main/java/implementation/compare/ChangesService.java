package implementation.compare;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitReference;
import git4idea.GitRevisionNumber;
import git4idea.actions.GitCompareWithRefAction;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import settings.GitScopeSettings;
import system.Defs;
import utils.PlatformApiReflection;
import utils.GitUtil;
import utils.ScopeRefRange;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicReference<ProgressIndicator> currentIndicator = new AtomicReference<>();
    private final AtomicLong collectionGeneration = new AtomicLong(0);

    public ChangesService(Project project) {
        this.project = project;
        this.git = project.getService(GitService.class);
    }

    @NotNull
    private static String getBranchToCompare(TargetBranchMap targetBranchByRepo, GitRepository repo, int repositoryCount) {
        String branchToCompare = targetBranchByRepo != null ? targetBranchByRepo.resolve(repo, repositoryCount) : null;
        if (branchToCompare == null) {
            branchToCompare = GitService.BRANCH_HEAD;
        }
        return branchToCompare;
    }

    // Cache for storing changes per repository (stores RepoChangesResult to preserve scope/local separation)
    private final Map<String, RepoChangesResult> changesCache = new ConcurrentHashMap<>();

    /**
     * Collects changes and reports them to {@code callBack}.
     *
     * <p>The callback is <em>always</em> invoked exactly once (unless the project is disposed
     * first): with the collected result, or with {@code null} when this collection was superseded
     * or cancelled by a newer one. Callers chain UI work on the callback, so dropping it — as a
     * cancelled {@code Task} used to — silently lost that work (e.g. the file-colors refresh
     * after a tab switch).
     */
    public void collectChangesWithCallback(TargetBranchMap targetBranchByRepo, Consumer<ChangesResult> callBack, boolean checkFs) {
        // Capture the current project reference to ensure consistency
        final Project currentProject = this.project;
        final GitService currentGitService = this.git;
        final long gen = collectionGeneration.incrementAndGet();

        /* Clear stale results at scheduling time, not inside run(): a task superseded before its
         * run() started never reached the clear, so entries cached DURING a git operation
         * (e.g. conflict-state changes mid-rebase) survived it and were served to the next
         * cache-permitted collection (issue #78). */
        if (checkFs) {
            changesCache.clear();
        }

        task = new Task.Backgroundable(currentProject, "Collecting " + Defs.APPLICATION_NAME, true) {

            private ChangesResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                currentIndicator.set(indicator);
                try {
                /* Early exit if disposing or superseded by a newer collection request.
                 * Nothing is applied and no callback runs, so this is a silent no-update: worth a
                 * line when tracking down a scope that stopped refreshing. */
                if (disposing.get() || indicator.isCanceled() || collectionGeneration.get() != gen) {
                    LOG.debug("Collection " + gen + " abandoned before start (disposing=" + disposing.get()
                            + ", cancelled=" + indicator.isCanceled()
                            + ", latestGeneration=" + collectionGeneration.get() + ")");
                    return;
                }

                Collection<Change> _changes = new ArrayList<>();
                Collection<Change> _scopeChanges = new ArrayList<>();
                Collection<Change> _localChanges = new ArrayList<>();
                List<String> errorRepos = new ArrayList<>();

                Collection<GitRepository> repositories = currentGitService.getRepositories();
                if (repositories.isEmpty()) {
                    /* Happens before the VCS mapping is ready; the scope stays empty until some
                     * later event triggers another collection. */
                    LOG.debug("Collection " + gen + ": no git repositories registered yet");
                }

                repositories.forEach(repo -> {
                    if (indicator.isCanceled() || collectionGeneration.get() != gen) {
                        LOG.debug("Collection " + gen + " interrupted at " + repo.getRoot().getPath()
                                + " (cancelled=" + indicator.isCanceled()
                                + ", latestGeneration=" + collectionGeneration.get() + ")");
                        return;
                    }
                    try {
                        String branchToCompare = getBranchToCompare(targetBranchByRepo, repo, repositories.size());

                        // Use repo path + target branch as cache key to ensure different branches don't share cache
                        String cacheKey = repo.getRoot().getPath() + "|" + branchToCompare;

                        RepoChangesResult repoResult;

                        if (!checkFs && changesCache.containsKey(cacheKey)) {
                            /* Use cached result (includes merged, scope, and local changes).
                             * A cache hit means the filesystem was NOT re-read for this repository. */
                            repoResult = changesCache.get(cacheKey);
                            LOG.debug("Collection " + gen + ": cache hit for " + cacheKey);
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
                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        throw e;
                    } catch (Exception e) {
                        /* Catch any unexpected errors from individual repo processing.
                         * This ensures one bad repo doesn't crash the entire operation. */
                        LOG.warn("Unexpected error processing repository " + repo.getRoot().getPath(), e);
                        errorRepos.add(repo.getRoot().getPath());
                    }
                });

                /* Return ERROR_STATE only if ALL repositories failed (e.g. commit hash not found in any repo).
                 * Individual repo failures are expected in multi-repo setups where a commit exists in only one repo. */
                if (!errorRepos.isEmpty() && errorRepos.size() == repositories.size()) {
                    LOG.debug("Collection " + gen + ": all " + errorRepos.size()
                            + " repositories failed -> ERROR_STATE");
                    result = new ChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>());
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Collection " + gen + " finished: merged=" + _changes.size()
                                + ", scope=" + _scopeChanges.size() + ", local=" + _localChanges.size()
                                + ", failedRepos=" + errorRepos.size());
                    }
                    result = new ChangesResult(_changes, _scopeChanges, _localChanges);
                }
                } finally {
                    currentIndicator.compareAndSet(indicator, null);
                }
            }

            @Override
            public void onSuccess() {
                // Ensure result is accessed only on the UI thread to update the UI component
                ApplicationManager.getApplication().invokeLater(() -> {
                    // Double-check the project is still valid
                    if (currentProject.isDisposed() || callBack == null) return;
                    if (this.result == null) {
                        /* The run() above returned early (superseded); a newer collection owns the
                         * model. Complete the callback with null so chained work still runs. */
                        LOG.debug("Collection " + gen + " superseded, completing callback without data");
                    }
                    callBack.accept(this.result);
                }, ModalityState.defaultModalityState(), __ -> disposing.get());
            }

            @Override
            public void onCancel() {
                /* Queueing a newer collection cancels this one's indicator; the platform then calls
                 * onCancel instead of onSuccess. The callback chain must still complete. */
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (currentProject.isDisposed() || callBack == null) return;
                    LOG.debug("Collection " + gen + " cancelled, completing callback without data");
                    callBack.accept(null);
                }, ModalityState.defaultModalityState(), __ -> disposing.get());
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.warn("Change collection " + gen + " failed, scope shows an error state", error);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!currentProject.isDisposed() && callBack != null) {
                        callBack.accept(new ChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>()));
                    }
                }, ModalityState.defaultModalityState(), __ -> disposing.get());
            }
        };
        ProgressIndicator prev = currentIndicator.getAndSet(null);
        if (prev != null) {
            prev.cancel();
        }

        /* Collect against a settled changelist. ChangeListManager restores the list persisted in
         * workspace.xml when the project opens and refreshes it only afterwards, so reading
         * getAllChanges() straight away can hand back entries for files git considers clean — or
         * that no longer exist at that path at all. Capture the task locally: a newer collection
         * reassigns the field before this callback runs. */
        final Task.Backgroundable queuedTask = task;
        ChangeListManager.getInstance(currentProject).invokeAfterUpdate(false, () -> {
            if (disposing.get() || currentProject.isDisposed()) {
                return;
            }
            queuedTask.queue();
        });
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
        boolean showDeletedFiles = GitScopeSettings.getInstance().isShowDeletedFiles();

        for (Change change : localChanges) {
            FilePath changePath = ChangesUtil.getFilePath(change);
            String changePathStr = changePath.getPath();

            if (change.getType() == Change.Type.DELETED) {
                if (!showDeletedFiles) {
                    continue;
                }
            } else if (!isPresentOnDisk(changePath)) {
                /* Stale changelist entry: nothing lives at this path any more, so rendering it would
                 * put a dead node in the tree. DELETED is exempt because absence is what it reports. */
                continue;
            }

            // Check if change belongs to this repository
            if (!changePathStr.startsWith(repoPath)) {
                continue;
            }

            // If existingChanges provided, skip duplicates
            if (existingChanges != null && !existingChanges.isEmpty()) {
                boolean isDuplicate = false;
                for (Change existing : existingChanges) {
                    String existingPath = ChangesUtil.getFilePath(existing).getPath();
                    if (changePathStr.equals(existingPath)) {
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

    /**
     * Reports whether a change still has a live file behind it.
     *
     * <p>A {@link VirtualFile} can outlive the file it points at when the deletion happened outside
     * the IDE, so validity is checked alongside presence.
     */
    private static boolean isPresentOnDisk(FilePath path) {
        VirtualFile virtualFile = path.getVirtualFile();
        return virtualFile != null && virtualFile.isValid();
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
        Collection<Change> scopeChanges;
        Collection<Change> mergedChanges;
        Collection<Change> repoLocalChanges;

        try {
            // Local Changes
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> localChanges = new ArrayList<>(changeListManager.getAllChanges());
            String repoPath = repo.getRoot().getPath();

            /* Add unversioned (untracked) files if the setting is enabled. They join the changelist
             * entries *before* filtering so they get the same repository and staleness checks —
             * appending them afterwards let untracked paths bypass both. */
            if (GitScopeSettings.getInstance().isShowUntrackedFiles()) {
                for (FilePath unversionedPath : changeListManager.getUnversionedFilesPaths()) {
                    localChanges.add(new Change(null, new CurrentContentRevision(unversionedPath), FileStatus.UNKNOWN));
                }
            }

            // Filter local changes for this repository
            repoLocalChanges = filterLocalChanges(localChanges, repoPath, null);

            // Special handling for HEAD - return local changes only, no scope changes
            if (scopeRef.equals(GitService.BRANCH_HEAD)) {
                return new RepoChangesResult(new ArrayList<>(repoLocalChanges), new ArrayList<>(), repoLocalChanges);
            }

            // Diff Changes - these are the pure scope changes
            GitRevisionNumber revisionNumber;
            if (ScopeRefRange.isRange(scopeRef)) {
                /* A range scope ("main..HEAD") asks for everything on HEAD since it diverged from the
                 * selected ref, so the base is their merge base. An unsupported range yields no ref
                 * and falls through to ERROR_STATE rather than being misread as a different diff. */
                String selectedRef = ScopeRefRange.selectedRef(scopeRef);
                revisionNumber = selectedRef == null ? null : GitUtil.resolveMergeBase(repo, selectedRef);
            } else {
                GitReference gitReference;

                // First try to find matching branch or tag (skip for relative refs like HEAD~1)
                gitReference = repo.getBranches().findBranchByName(scopeRef);
                if (gitReference == null && !scopeRef.contains("~") && !scopeRef.contains("^")) {
                    // ... try a tag
                    gitReference = PlatformApiReflection.findTagByName(repo, scopeRef);
                }

                if (gitReference == null) {
                    // Finally resort to try a generic reference (HEAD~2, <hash>, ...)
                    revisionNumber = GitUtil.resolveGitReference(repo, scopeRef);
                }
                else {
                    revisionNumber = new GitRevisionNumber(gitReference.getFullName());
                }
            }

            if (revisionNumber != null) {
                /* Diff a single base tree against HEAD. Range scopes pass their merge base, which makes
                 * the result the net pull-request diff instead of a union of every commit's changes. */
                scopeChanges = GitUtil.getDiffChanges(repo, file, revisionNumber);
                LOG.debug("ChangesService - Repository: " + repoPath + ", Scope: " + scopeRef + ", base: " + revisionNumber.asString() + ", scopeChanges count: " + scopeChanges.size());
            }
            else {
                // We do not have a valid GitReference => return ERROR_STATE
                LOG.debug("ChangesService - Repository: " + repoPath + ", Scope: " + scopeRef
                        + " could not be resolved to a revision -> ERROR_STATE");
                return new RepoChangesResult(ERROR_STATE, new ArrayList<>(), new ArrayList<>());
            }

            // Log what we collected
            LOG.debug("ChangesService - Repository: " + repoPath + ", localChanges count: " + repoLocalChanges.size());

            // Create merged changes: start with scope changes, then add local changes
            mergedChanges = new ArrayList<>(scopeChanges);

            // Add local changes that aren't already in the scope changes (excluding duplicates)
            Collection<Change> additionalLocalChanges = filterLocalChanges(repoLocalChanges, repoPath, scopeChanges);
            LOG.debug("ChangesService - Repository: " + repoPath + ", additionalLocalChanges count (after filtering): " + additionalLocalChanges.size());
            mergedChanges.addAll(additionalLocalChanges);

            LOG.debug("ChangesService - Repository: " + repoPath + ", Final counts - scope: " + scopeChanges.size() + ", local: " + repoLocalChanges.size() + ", merged: " + mergedChanges.size());

        } catch (VcsException e) {
            // Log VCS errors (e.g., locked files, git command failures) but don't fail entirely
            LOG.warn("Error collecting changes for repository " + repo.getRoot().getPath() + ": " + e.getMessage());
            return new RepoChangesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            // Catch any other unexpected errors (e.g., file system issues)
            LOG.warn("Unexpected error collecting changes for repository " + repo.getRoot().getPath(), e);
            return new RepoChangesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        return new RepoChangesResult(mergedChanges, scopeChanges, repoLocalChanges);
    }

}