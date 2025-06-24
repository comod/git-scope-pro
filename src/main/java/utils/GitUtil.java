package utils;

import com.intellij.openapi.project.Project;
import git4idea.GitReference;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for Git operations
 */
public class GitUtil {

    /**
     * Resolves a Git reference (full or partial hash, tag, or other commit-ish ref,
     * excluding local branches) to a GitReference object. Returns null if resolution
     * fails or if the refSpec is a local branch name.
     *
     * @param repository the target GitRepository
     * @param refSpec    the Git ref (commit hash, tag name, "HEAD~1", etc.)
     * @return a GitReference wrapping the resolved hash, or null if it could not be resolved or is a branch
     */
    @Nullable
    public static GitReference resolveGitReference(@NotNull GitRepository repository,
                                                   @NotNull String refSpec) {
        // 1) Exclude local branch names
        boolean isBranch = repository.getBranches()
                .getLocalBranches()
                .stream()
                .map(GitReference::getName)
                .anyMatch(name -> name.equals(refSpec));
        if (isBranch) {
            return null;
        }

        Project project = repository.getProject();
        Git git = Git.getInstance();

        // 2) Try `git rev-parse --verify <refSpec>^{commit}` for all commit-ish refs
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.REV_PARSE);
        handler.addParameters("--verify", refSpec + "^{commit}");
        try {
            String hash = git.runCommand(handler)
                    .getOutputOrThrow()
                    .trim();
            if (!hash.isEmpty()) {
                return wrapHashAsReference(hash);
            }
        } catch (Exception ignored) {
            // resolution failed, fall through
        }

        // 3) Nothing matched
        return null;
    }

    /**
     * Wraps a 40-character commit hash in a GitReference implementation
     */
    @NotNull
    private static GitReference wrapHashAsReference(@NotNull String fullHash) {
        // Ensure it's 40 hex chars
        if (fullHash.length() != 40) {
            throw new IllegalArgumentException("Invalid hash length: " + fullHash);
        }
        return new GitReference(fullHash) {
            @Override
            public @NotNull String getName() {
                // short form (first 7 chars)
                return fullHash.substring(0, 7);
            }

            @Override
            public @NotNull String getFullName() {
                return fullHash;
            }
        };
    }
}
