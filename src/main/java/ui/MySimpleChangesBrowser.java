package ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.VcsDataKeys.SELECTED_CHANGES;

public class MySimpleChangesBrowser extends SimpleChangesBrowser {

    public MySimpleChangesBrowser(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
        super(project, changes);
    }

    protected void onDoubleClick() {
        @NotNull List<Change> changes = getSelectedChanges();

        for (Change change : changes) {
            new OpenFileDescriptor(myProject, Objects.requireNonNull(change.getVirtualFile()), 0, 0, false).navigate(true);
        }
    }

}
