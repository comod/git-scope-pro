package utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the Git Scope diff request producer for a change: base scope revision (decorated with the
 * active scope name) on the left vs. the current working-tree content on the right.
 *
 * <p>Shared by the changes browser (right-click "Show Diff") and the Show Diff keyboard action so
 * both present exactly the same diff.
 */
public final class ScopeDiff {

    private ScopeDiff() {
    }

    /**
     * Builds a diff producer showing {@code change}'s base revision vs. the current working content.
     * Returns null when the change has no usable file path.
     */
    public static @Nullable ChangeDiffRequestChain.Producer buildProducer(@NotNull Project project,
                                                                          @NotNull Change change,
                                                                          @NotNull String scopeDisplayName) {
        FilePath filePath = ChangesUtil.getAfterPath(change);
        if (filePath == null) {
            filePath = ChangesUtil.getBeforePath(change);
        }
        if (filePath == null || filePath.getVirtualFile() == null) {
            return null;
        }
        Change modifiedChange = new Change(
                withScopeName(change.getBeforeRevision(), scopeDisplayName),
                new CurrentContentRevision(filePath)
        );
        return ChangeDiffRequestProducer.create(project, modifiedChange);
    }

    /** Decorates a revision's displayed number with the scope name, e.g. "abc1234 (master)". */
    public static @Nullable ContentRevision withScopeName(@Nullable ContentRevision original, @NotNull String scopeName) {
        if (original == null || scopeName.isEmpty()) return original;
        return new ContentRevision() {
            @Override public @Nullable String getContent() throws VcsException { return original.getContent(); }
            @Override public @NotNull FilePath getFile() { return original.getFile(); }
            @Override public @NotNull VcsRevisionNumber getRevisionNumber() {
                VcsRevisionNumber base = original.getRevisionNumber();
                return new VcsRevisionNumber() {
                    @Override public @NotNull String asString() { return base.asString() + " (" + scopeName + ")"; }
                    @Override public int compareTo(@NotNull VcsRevisionNumber o) { return base.compareTo(o); }
                };
            }
        };
    }
}
