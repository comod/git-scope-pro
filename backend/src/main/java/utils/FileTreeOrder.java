package utils;

import com.intellij.ide.util.treeView.FileNameComparator;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Orders file paths the way a file tree shows them: walking down into a directory before moving on
 * to the next entry of the level above, subdirectories before files within each directory, and
 * names compared naturally — case-insensitively, with digit runs read as numbers.
 *
 * <p>Change navigation uses this so stepping past the last change of a file lands on the file the
 * Git Scope tree shows next. Sorting the absolute paths as plain strings instead — which is what
 * navigation used to do — produces an order that has little to do with the tree: uppercase names
 * sort before all lowercase ones, and a file sorts among the contents of a sibling directory
 * whenever their names share a prefix.
 *
 * <p>This mirrors the platform's own {@code HierarchicalFilePathComparator.NATURAL}, which the
 * changes tree sorts with. It is reimplemented here rather than called because that class is
 * marked internal, while the name comparison it delegates to is not; comparing paths as strings
 * also keeps this testable without an IDE.
 */
public final class FileTreeOrder implements Comparator<String> {

    public static final FileTreeOrder INSTANCE = new FileTreeOrder();

    private FileTreeOrder() {}

    @Override
    public int compare(@NotNull String path1, @NotNull String path2) {
        int start = 0;
        while (true) {
            int end1 = path1.indexOf('/', start);
            int end2 = path2.indexOf('/', start);

            // A segment with more path after it is a directory; the last segment is the file.
            boolean isDirectory1 = end1 != -1;
            boolean isDirectory2 = end2 != -1;
            if (isDirectory1 != isDirectory2) {
                // Same level, one descends and one does not: directories come first, whatever the
                // names are. This is what makes navigation walk a directory to its end before
                // continuing with the files beside it.
                return isDirectory1 ? -1 : 1;
            }

            String name1 = isDirectory1 ? path1.substring(start, end1) : path1.substring(start);
            String name2 = isDirectory2 ? path2.substring(start, end2) : path2.substring(start);

            int byName = FileNameComparator.getInstance().compare(name1, name2);
            if (byName != 0) return byName;

            if (!isDirectory1) {
                // Same name and both are the final segment: identical paths apart from case, which
                // the natural comparison ignores. Fall back to an exact comparison so the order
                // stays stable rather than reporting two distinct files as equal.
                return path1.compareTo(path2);
            }

            // Same directory name: continue with the next level.
            start = end1 + 1;
        }
    }
}
