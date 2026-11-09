package service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import implementation.compare.ChangesService;
import model.MyModel;
import model.TargetBranchMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import toolwindow.elements.VcsTree;

import java.io.File;
import java.util.*;

/** Files marked as reviewed in the Git Scope tree, per repository and scope (#120). */
@State(name = "GitScopeReviewedFiles", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ReviewedFilesService implements PersistentStateComponent<ReviewedFilesService.ReviewMarksState> {
    private static final int MAX_SCOPES = 50;

    public static class ReviewMarksState {
        /** Scope key to (repository-relative path to fingerprint), least recently used first. */
        @MapAnnotation(sortBeforeSave = false)
        public LinkedHashMap<String, Map<String, String>> scopes = new LinkedHashMap<>();
    }

    private record Location(String scope, String path, File file) {}

    private final Project project;
    private ReviewMarksState data = new ReviewMarksState();

    public ReviewedFilesService(Project project) {
        this.project = project;
    }

    @Override
    public synchronized ReviewMarksState getState() {
        ReviewMarksState copy = new ReviewMarksState();
        data.scopes.forEach((scope, marks) -> copy.scopes.put(scope, new HashMap<>(marks)));
        return copy;
    }

    @Override
    public synchronized void loadState(@NotNull ReviewMarksState state) {
        data = state;
    }

    public boolean hidesReviewed() {
        state.State toggles = project.getService(state.State.class);
        return toggles.getReviewModeEnabled() && toggles.getHideReviewedFiles();
    }

    /** Whether the file is marked and unchanged since; removes the mark of a file that changed. */
    public synchronized boolean isReviewed(MyModel model, Change change) {
        Location loc = locate(model, change);
        Map<String, String> marks = loc == null ? null : touch(loc.scope, false);
        String stored = marks == null ? null : marks.get(loc.path);
        if (stored == null) return false;
        if (fingerprint(loc.file).equals(stored)) return true;
        marks.remove(loc.path);
        if (marks.isEmpty()) data.scopes.remove(loc.scope);
        return false;
    }

    public synchronized void setReviewed(MyModel model, Change change, boolean reviewed) {
        Location loc = locate(model, change);
        if (loc == null) return;
        Map<String, String> marks = touch(loc.scope, true);
        if (reviewed) {
            marks.put(loc.path, fingerprint(loc.file));
        } else {
            marks.remove(loc.path);
            if (marks.isEmpty()) data.scopes.remove(loc.scope);
        }
    }

    public synchronized void clear(MyModel model) {
        for (GitRepository repo : GitRepositoryManager.getInstance(project).getRepositories()) {
            data.scopes.remove(scopeKey(model, repo));
        }
    }

    public synchronized void clearAll() {
        data.scopes.clear();
    }

    public synchronized String stats() {
        int files = data.scopes.values().stream().mapToInt(Map::size).sum();
        return data.scopes.size() + " scopes, " + files + " files";
    }

    /** Redraws the Git Scope tree after marks or review toggles changed. */
    public void refreshTree() {
        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        VcsTree tree = toolWindowService == null ? null : toolWindowService.getVcsTree();
        if (tree != null) tree.refreshReviewed();
    }

    /** Fingerprint of a file on disk: {@code size:timestamp}, {@code 0:0} for a deleted file. */
    static String fingerprint(File file) {
        return file.length() + ":" + file.lastModified();
    }

    /** Moves a scope to the most recently used end, creating it when asked; null when absent and not created. */
    private @Nullable Map<String, String> touch(String scope, boolean create) {
        Map<String, String> marks = data.scopes.remove(scope);
        if (marks == null && !create) return null;
        data.scopes.put(scope, marks != null ? marks : new HashMap<>());
        int maxScopes = MAX_SCOPES * Math.max(1, GitRepositoryManager.getInstance(project).getRepositories().size());
        if (data.scopes.size() > maxScopes) data.scopes.remove(data.scopes.keySet().iterator().next());
        return data.scopes.get(scope);
    }

    private @Nullable Location locate(MyModel model, Change change) {
        FilePath file = ChangesUtil.getFilePath(change);
        GitRepository repo = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file);
        if (repo == null) return null;
        String path = FileUtil.getRelativePath(repo.getRoot().getPath(), file.getPath(), '/');
        return new Location(scopeKey(model, repo), path, file.getIOFile());
    }

    private String scopeKey(MyModel model, GitRepository repo) {
        // Detached HEAD or no commit yet: HEAD stands in for the branch, so new commits keep the marks.
        String source = Objects.requireNonNullElse(repo.getCurrentBranchName(), GitService.BRANCH_HEAD);
        int repositoryCount = GitRepositoryManager.getInstance(project).getRepositories().size();
        String target = ChangesService.getBranchToCompare(model.getTargetBranchMap(), repo, repositoryCount);
        return TargetBranchMap.repoKey(repo) + "|" + source + "|" + target;
    }
}
