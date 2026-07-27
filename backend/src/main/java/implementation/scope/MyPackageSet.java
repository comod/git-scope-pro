package implementation.scope;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PackageSet implementation that keeps a path-indexed view of changes without touching the VFS.
 */
public class MyPackageSet implements PackageSet {

    private volatile Map<String, Change> changesByPath = Collections.emptyMap();

    public void setChanges(@NotNull Collection<Change> changes) {
        if (changes.isEmpty()) {
            changesByPath = Collections.emptyMap();
            return;
        }

        Map<String, Change> map = new LinkedHashMap<>(Math.max(16, changes.size() * 2));
        for (Change change : changes) {
            if (change == null) continue;

            // Prefer "after" path (new location), fall back to "before" path
            FilePath after = ChangesUtil.getAfterPath(change);
            FilePath before = ChangesUtil.getBeforePath(change);

            String key = null;
            if (after != null && !after.isDirectory()) {
                key = normalizePath(after.getPath());
            } else if (before != null && !before.isDirectory()) {
                key = normalizePath(before.getPath());
            }

            if (key != null && !key.isEmpty()) {
                map.put(key, change);
            }
        }

        changesByPath = Collections.unmodifiableMap(map);
    }

    /**
     * Exposes an immutable snapshot of the current path -> change map.
     */
    public @NotNull Map<String, Change> getChangesByPath() {
        Map<String, Change> snapshot = changesByPath;
        return snapshot == null ? Collections.emptyMap() : snapshot;
    }

    /**
     * Convenience accessor returning the current changes snapshot.
     */
    public @NotNull Collection<Change> getChanges() {
        Map<String, Change> snapshot = changesByPath;
        return snapshot == null ? Collections.emptyList() : snapshot.values();
    }

    /**
     * PackageSet: checks whether the given file is part of the current path-indexed changes.
     * (for performance => does not perform any filesystem operations)
     */
    @Override
    public boolean contains(@Nullable PsiFile file, @NotNull NamedScopesHolder holder) {
        if (file == null) {
            return false;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        String key = normalizePath(vFile.getPath());
        Map<String, Change> snapshot = changesByPath;
        return snapshot != null && snapshot.containsKey(key);
    }

    @Override
    public @NotNull PackageSet createCopy() {
        MyPackageSet copy = new MyPackageSet();
        Map<String, Change> snapshot = this.changesByPath;
        copy.changesByPath = (snapshot == null) ? Collections.emptyMap() : snapshot;
        return copy;
    }

    @Override
    public @NotNull String getText() {
        return "GitScopeFiles";
    }

    private static @NotNull String normalizePath(@NotNull String rawPath) {
        String normalized;
        try {
            // Resolve "." and ".."
            normalized = Paths.get(rawPath).normalize().toString();
        } catch (InvalidPathException ignored) {
            // Fallback to the original if it cannot be parsed
            normalized = rawPath;
        }

        // Convert to system-independent separators
        normalized = FileUtil.toSystemIndependentName(normalized);

        // Normalize case if filesystem is case-insensitive (e.g., Windows, default macOS)
        if (!SystemInfo.isFileSystemCaseSensitive) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    @Override
    public int getNodePriority() {
        return 0;
    }
}