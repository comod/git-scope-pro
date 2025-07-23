
package implementation.scope;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public class MyPackageSet implements PackageSet {

    private Collection<Change> changes;

    public void setChanges(Collection<Change> changes) {
        this.changes = changes;
    }

    @Override
    public boolean contains(@Nullable PsiFile file, @NotNull NamedScopesHolder holder) {
        // Handle null file parameter gracefully
        if (file == null) {
            return false;
        }

        if (this.changes == null) {
            return false;
        }

        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return false;
        }

        for (Change change : this.changes) {
            VirtualFile vFileOfChanges = change.getVirtualFile();

            if (vFileOfChanges == null) {
                continue;
            }

            if (Objects.equals(vFile, vFileOfChanges)) {
                return true;
            }
        }

        return false;
    }

    @NotNull
    @Override
    public PackageSet createCopy() {
        MyPackageSet copy = new MyPackageSet();
        copy.setChanges(this.changes);
        return copy;
    }

    @NotNull
    @Override
    public String getText() {
        return "GitScope Files";
    }

    @Override
    public int getNodePriority() {
        return 0;
    }
}