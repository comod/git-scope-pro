package model;

import com.intellij.openapi.util.io.FileUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @param value Repo key (see {@link #repoKey}) to BranchToCompare
 */
public record TargetBranchMap(Map<String, String> value) {

    public static TargetBranchMap create() {
        Map<String, String> map = new HashMap<>();
        return new TargetBranchMap(map);
    }

    public void add(GitRepository repo, String branch) {
        this.value.put(repoKey(repo), branch);
    }

    /**
     * Resolves the branch stored for {@code repo}. See {@link #resolve(String, String, int)} for
     * the lookup order; this overload just supplies the live repo's current and legacy keys.
     */
    @Nullable
    public String resolve(GitRepository repo, int repositoryCount) {
        return resolve(repoKey(repo), repo.toString(), repositoryCount);
    }

    /**
     * Resolves the branch stored under {@code relativeKey}. Tries, in order: {@code relativeKey}
     * itself (the current project-relative key), {@code legacyKey} (the pre-#110 absolute-path key,
     * for configs saved before that fix), and -- for single-repo projects only -- the map's sole
     * entry regardless of its key, so a repo that moved outside the project's relative layout still
     * resolves instead of silently falling back to HEAD. A fallback match rewrites the map to
     * {@code relativeKey}, so the next save self-heals it.
     *
     * <p>Kept free of platform types (no {@link GitRepository}) so the lookup order can be unit
     * tested directly.
     *
     * @param repositoryCount number of repositories currently registered in the project
     */
    @Nullable
    public String resolve(String relativeKey, String legacyKey, int repositoryCount) {
        String branch = value.get(relativeKey);
        if (branch != null) {
            return branch;
        }

        branch = value.get(legacyKey);
        if (branch != null) {
            migrate(legacyKey, relativeKey);
            return branch;
        }

        if (repositoryCount == 1 && value.size() == 1) {
            Map.Entry<String, String> onlyEntry = value.entrySet().iterator().next();
            migrate(onlyEntry.getKey(), relativeKey);
            return onlyEntry.getValue();
        }

        return null;
    }

    private void migrate(String oldKey, String newKey) {
        if (!oldKey.equals(newKey)) {
            value.put(newKey, value.remove(oldKey));
        }
    }

    /** Project-relative path of {@code repo}'s root, stable when the whole project moves. */
    public static String repoKey(GitRepository repo) {
        return repoKey(repo.getProject().getBasePath(), repo.getRoot().getPath());
    }

    /**
     * Path of {@code repoPath} relative to {@code basePath}, stable when the whole project moves.
     * Falls back to the (system-independent) absolute path when there's no common ancestor, e.g.
     * the repo and project base live on different drives on Windows.
     *
     * <p>Kept free of platform types so it can be unit tested directly.
     */
    public static String repoKey(@Nullable String basePath, String repoPath) {
        String relative = basePath != null ? FileUtil.getRelativePath(basePath, repoPath, '/') : null;
        return relative != null ? relative : FileUtil.toSystemIndependentName(repoPath);
    }
}
