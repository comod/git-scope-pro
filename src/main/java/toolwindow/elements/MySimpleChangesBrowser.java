package toolwindow.elements;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
