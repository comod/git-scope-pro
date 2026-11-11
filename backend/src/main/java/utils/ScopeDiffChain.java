package utils;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Diff chain whose file list can shrink while the diff is open. The platform processor reads
 * {@link #getRequests()} on every previous/next step and in the file counter, so a removed file
 * disappears from the navigation after a reload of the current request.
 */
public final class ScopeDiffChain extends ChangeDiffRequestChain {

    /** Chain user data key: the diff context exposes the chain user data to diff actions. */
    public static final Key<ScopeDiffChain> KEY = Key.create("GitScope.ScopeDiffChain");

    private final List<Producer> producers;
    private final int selectedIndex;

    public ScopeDiffChain(@NotNull List<? extends Producer> producers, int selectedIndex) {
        super(producers, selectedIndex);
        this.producers = new ArrayList<>(producers);
        this.selectedIndex = selectedIndex;
        putUserData(KEY, this);
    }

    @Override
    public @NotNull List<? extends Producer> getRequests() {
        return producers;
    }

    @Override
    public @NotNull ListSelection<? extends Producer> getListSelection() {
        return ListSelection.createAt(producers, Math.max(0, Math.min(selectedIndex, producers.size() - 1)));
    }

    /**
     * Removes the file from the chain unless it is the last one: the processor keeps its own index,
     * which points at the next file after a removal but would be out of range for the last file.
     *
     * @return whether the file was removed
     */
    public boolean removeUnlessLast(@NotNull FilePath file) {
        for (int i = 0; i < producers.size() - 1; i++) {
            if (file.equals(producers.get(i).getFilePath())) {
                producers.remove(i);
                return true;
            }
        }
        return false;
    }
}
