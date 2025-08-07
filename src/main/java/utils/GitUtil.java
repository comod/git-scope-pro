package utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

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
     * @return a GitRevisionNumber
     */
    @Nullable
    public static GitRevisionNumber resolveGitReference(@NotNull GitRepository repository,
                                                   @NotNull String refSpec) {

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
                return new GitRevisionNumber(hash);
            }
        } catch (Exception ignored) {
            // resolution failed, fall through
        }

        // 3) Nothing matched
        return null;
    }

    public static @NotNull Collection<Change> getDiffChanges(@NotNull GitRepository repository,
                                                             @NotNull VirtualFile file,
                                                             @NotNull GitRevisionNumber revisionNumber) throws VcsException {
        FilePath filePath = VcsUtil.getFilePath(file);

        Project project = repository.getProject();
        Collection<Change> changes =
                GitChangeUtils.getDiffWithWorkingDir(project, repository.getRoot(), revisionNumber.toString(), Collections.singletonList(filePath), false);

        if (changes.isEmpty() && GitHistoryUtils.getCurrentRevision(project, filePath, revisionNumber.toString()) == null) {
            throw new VcsException("Could not get diff for base file:" + file + " and revision: " + revisionNumber);
        }

        ContentRevision contentRevision = GitContentRevision.createRevision(filePath, revisionNumber, project);
        return changes.isEmpty() && !filePath.isDirectory() ? createChangesWithCurrentContentForFile(filePath, contentRevision) : changes;
    }

}
