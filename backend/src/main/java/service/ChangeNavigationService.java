package service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import implementation.gutter.Range;
import implementation.gutter.RangesBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rpc.ChangeNavDirection;
import system.Defs;
import utils.FileOpener;
import utils.FileTreeOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Backend service that powers the "next/previous change" and "next/previous changed file"
 * navigation actions.
 *
 * <p>The set of changed files and their change ranges are computed from the active scope changes
 * (the same source the gutter uses), so navigation follows the visible gutter markers. The
 * authoritative change data lives on the backend, and file opening + caret placement go through the
 * canonical {@link FileOpener} path (which honors the preview-tab setting), so this works correctly
 * in both monolith and split/remote mode.
 */
@Service(Service.Level.PROJECT)
public final class ChangeNavigationService {

    private static final Logger LOG = Defs.getLogger(ChangeNavigationService.class);

    private final Project project;

    // Last position we navigated to. Used as a fallback when the frontend reports no focused
    // editor (a transient race right after opening a file without focus), so navigation continues
    // from where we were instead of resetting to the first file.
    private volatile String lastNavigatedFile = null;
    private volatile int lastNavigatedLine = -1;

    // Cache of the diff editor-tab file per changed-file path. Reusing the same
    // ChainDiffVirtualFile instance means DiffEditorTabFilesManager focuses the already-open diff
    // tab instead of opening a duplicate. Each entry is tagged with the scope base revision it was
    // built from, so switching scope/target rebuilds the diff instead of focusing a stale one.
    private final Map<String, CachedDiff> openDiffFiles = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedDiff(com.intellij.diff.editor.ChainDiffVirtualFile file, String signature) {
    }

    public ChangeNavigationService(Project project) {
        this.project = project;
    }

    /**
     * Navigates relative to the caret in the currently focused editor.
     *
     * @param currentFilePath path of the focused editor's file, or null if none
     * @param caretLine       0-based caret line in that editor
     * @param direction       navigation direction
     */
    public void navigate(@Nullable String currentFilePath, int caretLine, @NotNull ChangeNavDirection direction) {
        ViewService viewService = project.getService(ViewService.class);
        if (viewService == null) return;

        Map<String, Change> scopeChanges = viewService.getScopeChangesMap();
        Map<String, Change> localChanges = viewService.getLocalChangesTowardsHeadMap();
        if (scopeChanges == null) scopeChanges = Collections.emptyMap();
        if (localChanges == null) localChanges = Collections.emptyMap();
        if (scopeChanges.isEmpty() && localChanges.isEmpty()) {
            LOG.debug("ChangeNavigation: no scope or local changes");
            return;
        }

        // Ordered list of changed files: the union of scope changes and local (working-tree vs
        // HEAD) changes, so navigation visits every file that shows a gutter marker — both the
        // scope markers we paint and the local markers the IDE paints.
        List<String> files = orderedFiles(scopeChanges, localChanges);
        if (files.isEmpty()) {
            // Everything in the scope is deleted, a directory, or otherwise not openable.
            LOG.debug("ChangeNavigation: no navigable files in scope");
            return;
        }

        // Fall back to our last navigated position when the frontend has no focused editor
        // (or the focused editor isn't one of the changed files). This keeps sequential
        // next/previous presses moving forward instead of snapping back to the first file.
        String fromFile = currentFilePath;
        int fromLine = caretLine;
        if (fromFile == null || !files.contains(fromFile)) {
            if (lastNavigatedFile != null && files.contains(lastNavigatedFile)) {
                fromFile = lastNavigatedFile;
                fromLine = lastNavigatedLine;
            }
        }

        switch (direction) {
            case NEXT_CHANGE -> navigateChange(files, scopeChanges, localChanges, fromFile, fromLine, true);
            case PREVIOUS_CHANGE -> navigateChange(files, scopeChanges, localChanges, fromFile, fromLine, false);
            case NEXT_FILE -> navigateFile(files, scopeChanges, localChanges, fromFile, true);
            case PREVIOUS_FILE -> navigateFile(files, scopeChanges, localChanges, fromFile, false);
        }
    }

    /**
     * Shows the Git Scope diff (base scope revision vs. current working content) for the focused
     * file, as an editor tab. Falls back to the last navigated file, then the Git Scope tree
     * selection, when no changed file is focused.
     */
    public void showDiff(@Nullable String currentFilePath) {
        ViewService viewService = project.getService(ViewService.class);
        if (viewService == null) return;

        Map<String, Change> scopeChanges = viewService.getScopeChangesMap();
        if (scopeChanges == null || scopeChanges.isEmpty()) {
            LOG.debug("ShowDiff: no scope changes");
            return;
        }

        String targetPath = resolveDiffTarget(currentFilePath, scopeChanges);
        if (targetPath == null) {
            LOG.debug("ShowDiff: no target file");
            return;
        }
        Change change = scopeChanges.get(targetPath);
        if (change == null) return;

        String scopeName = scopeDisplayName(viewService);
        final String path = targetPath;
        final String signature = changeSignature(change);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            var fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
            var diffManager = com.intellij.diff.editor.DiffEditorTabFilesManager.getInstance(project);

            // Reuse (focus) the existing Git Scope diff tab only when it is still open AND was built
            // for the same scope base revision. If the scope/target changed, the signature differs
            // and we rebuild the diff for the new scope.
            var cached = openDiffFiles.get(path);
            if (cached != null && cached.signature().equals(signature) && fem.isFileOpen(cached.file())) {
                diffManager.showDiffFile(cached.file(), true);
                highlightTreeFor(path);
                return;
            }
            // Stale (closed or different scope): drop it and, if a stale tab is still open, close it
            // so we don't leave an outdated diff around.
            if (cached != null && fem.isFileOpen(cached.file())) {
                fem.closeFile(cached.file());
            }
            openDiffFiles.remove(path);

            var producer = utils.ScopeDiff.buildProducer(project, change, scopeName);
            if (producer == null) return;
            var chain = com.intellij.diff.chains.SimpleDiffRequestChain.fromProducer(producer);
            String title = new java.io.File(path).getName();
            var diffFile = new com.intellij.diff.editor.ChainDiffVirtualFile(chain, title);
            openDiffFiles.put(path, new CachedDiff(diffFile, signature));
            diffManager.showDiffFile(diffFile, true);
            highlightTreeFor(path);
        });
    }

    /** Signature capturing the scope base revision a diff was built from (rebuild when it changes). */
    private static String changeSignature(Change change) {
        var before = change.getBeforeRevision();
        if (before == null) return "none";
        try {
            return before.getRevisionNumber().asString();
        } catch (Exception e) {
            return "none";
        }
    }

    private void highlightTreeFor(String path) {
        VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
        if (vf != null) highlightInToolWindow(vf);
    }

    private @Nullable String resolveDiffTarget(@Nullable String currentFilePath, Map<String, Change> scopeChanges) {
        if (currentFilePath != null && scopeChanges.containsKey(currentFilePath)) {
            return currentFilePath;
        }
        // Fallback: the last file we navigated to (e.g. when a diff tab is currently focused, so the
        // frontend has no underlying changed-file editor).
        if (lastNavigatedFile != null && scopeChanges.containsKey(lastNavigatedFile)) {
            return lastNavigatedFile;
        }
        return null;
    }

    private String scopeDisplayName(ViewService viewService) {
        try {
            model.MyModel m = viewService.getCurrent();
            if (m != null) return m.getDisplayName();
        } catch (Exception ignored) {
        }
        return "";
    }

    // --- Change-level navigation (within a file, crossing file boundaries at the ends) ---

    private void navigateChange(List<String> files, Map<String, Change> scopeChanges,
                                Map<String, Change> localChanges,
                                @Nullable String currentFilePath, int caretLine, boolean forward) {
        int fileIdx = currentFilePath == null ? -1 : files.indexOf(currentFilePath);

        if (fileIdx >= 0) {
            List<Integer> lines = changeStartLines(currentFilePath, scopeChanges.get(currentFilePath),
                    localChanges.get(currentFilePath));
            Integer target = forward ? firstLineAfter(lines, caretLine) : firstLineBefore(lines, caretLine);
            if (target != null) {
                open(currentFilePath, target);
                return;
            }
            // Past the last/first change of this file -> move to the adjacent file.
            int nextIdx = wrapIndex(fileIdx + (forward ? 1 : -1), files.size());
            openFileAtEdge(files, scopeChanges, localChanges, nextIdx, forward);
            return;
        }

        // No current file (or caret not in a changed file): jump to the first/last change overall.
        int startIdx = forward ? 0 : files.size() - 1;
        openFileAtEdge(files, scopeChanges, localChanges, startIdx, forward);
    }

    // --- File-level navigation (cycle between changed files) ---

    private void navigateFile(List<String> files, Map<String, Change> scopeChanges,
                              Map<String, Change> localChanges,
                              @Nullable String currentFilePath, boolean forward) {
        int fileIdx = currentFilePath == null ? -1 : files.indexOf(currentFilePath);
        int targetIdx;
        if (fileIdx < 0) {
            targetIdx = forward ? 0 : files.size() - 1;
        } else {
            targetIdx = wrapIndex(fileIdx + (forward ? 1 : -1), files.size());
        }
        // Always land on the first change of the target file when cycling files.
        openFileAtEdge(files, scopeChanges, localChanges, targetIdx, true);
    }

    /**
     * Opens the file at {@code files[idx]} placing the caret on its first change (when moving
     * forward) or last change (when moving backward). Falls back to line 0 if ranges can't be
     * computed.
     */
    private void openFileAtEdge(List<String> files, Map<String, Change> scopeChanges,
                                Map<String, Change> localChanges, int idx, boolean firstChange) {
        if (idx < 0 || idx >= files.size()) return;
        String path = files.get(idx);
        List<Integer> lines = changeStartLines(path, scopeChanges.get(path), localChanges.get(path));
        int line = 0;
        if (!lines.isEmpty()) {
            line = firstChange ? lines.get(0) : lines.get(lines.size() - 1);
        }
        open(path, line);
    }

    private void open(String path, int line) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) {
            LOG.debug("ChangeNavigation: could not resolve file " + path);
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("ChangeNavigation: opening " + displayPath(path)
                    + " [" + file.getFileType().getName() + "] at line " + (line + 1));
        }
        lastNavigatedFile = path;
        lastNavigatedLine = line;

        // Stepping through changes from the tool window should leave the focus there. Asking for the
        // opened editor to be focused takes it away, and on Linux the Project View following the
        // file ("Autoscroll from Source") can then keep it, so the next keystroke no longer reaches
        // the tool window at all. Focusing the editor is only what we want when navigation was
        // triggered from the editor in the first place.
        //
        // Dropping the focus request costs nothing: the frontend then reports no focused changed
        // file, and navigate() continues from lastNavigatedFile/lastNavigatedLine instead.
        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        boolean startedInToolWindow = toolWindowService != null && toolWindowService.isFocused();

        FileOpener.openAndGoToLine(project, file, line, !startedInToolWindow);
        highlightInToolWindow(toolWindowService, file);
        if (startedInToolWindow) {
            toolWindowService.restoreFocus();
        }
    }

    /** Selects/highlights the file's change in the Git Scope tool window tree, so the tree stays in sync. */
    private void highlightInToolWindow(@NotNull VirtualFile file) {
        highlightInToolWindow(project.getService(ToolWindowServiceInterface.class), file);
    }

    private void highlightInToolWindow(@Nullable ToolWindowServiceInterface toolWindowService,
                                       @NotNull VirtualFile file) {
        if (toolWindowService == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            toolWindowService.selectFile(file);
        });
    }

    // --- Range helpers ---

    /**
     * Returns the sorted, de-duplicated 0-based start lines of all changes in the given file —
     * the union of scope changes and local (working-tree vs HEAD) changes, so navigation stops on
     * every visible gutter marker.
     *
     * <p>Prefers the ranges already cached by the gutter pipeline (exact match with the visible
     * markers) for open files: {@code cached.ranges} are the scope markers and
     * {@code cached.localRanges} are the local markers, both in current-document space. For files
     * with no cached data (not opened yet) it computes both range sets on demand.
     */
    private List<Integer> changeStartLines(String path, @Nullable Change scopeChange, @Nullable Change localChange) {
        java.util.TreeSet<Integer> lines = new java.util.TreeSet<>();

        GutterDataService gds = project.getService(GutterDataService.class);
        GutterDataService.GutterFileData cached = gds != null ? gds.getData(path) : null;
        if (cached != null) {
            if (cached.ranges != null) {
                for (Range r : cached.ranges) lines.add(r.getLine1());
            }
            if (cached.localRanges != null) {
                for (Range r : cached.localRanges) lines.add(r.getLine1());
            }
            if (!lines.isEmpty()) return new ArrayList<>(lines);
        }

        // No cached gutter data (file not open): compute scope and local ranges on demand.
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        String currentContent = file == null ? null : ApplicationManager.getApplication().runReadAction(
                (Computable<String>) () -> {
                    Document doc = FileDocumentManager.getInstance().getDocument(file);
                    return doc != null ? doc.getImmutableCharSequence().toString() : null;
                });
        if (currentContent == null) return new ArrayList<>(lines);
        String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);

        for (Range r : computeRanges(path, scopeChange, normalizedCurrent)) lines.add(r.getLine1());
        for (Range r : computeRanges(path, localChange, normalizedCurrent)) lines.add(r.getLine1());
        return new ArrayList<>(lines);
    }

    /**
     * Computes change ranges (before-revision vs. current content) for a file not covered by cached
     * gutter data. Returns ranges in current-document coordinate space (line1/line2 are current-side).
     */
    private List<Range> computeRanges(String path, @Nullable Change change, String normalizedCurrent) {
        if (change == null || change.getBeforeRevision() == null) return Collections.emptyList();

        String baseContent;
        try {
            baseContent = change.getBeforeRevision().getContent();
        } catch (VcsException e) {
            LOG.debug("ChangeNavigation: error getting base content for " + path, e);
            return Collections.emptyList();
        }
        if (baseContent == null) return Collections.emptyList();

        String normalizedBase = StringUtil.convertLineSeparators(baseContent);
        try {
            return RangesBuilder.INSTANCE.createRanges(normalizedCurrent, normalizedBase);
        } catch (Exception e) {
            LOG.debug("ChangeNavigation: error computing ranges for " + path, e);
            return Collections.emptyList();
        }
    }

    // --- file ordering ---

    /**
     * The changed files in the order navigation should visit them: the order the Git Scope tree
     * displays, when it has one.
     *
     * <p>The tree's order depends on its grouping — by module, repository or directory, switchable
     * from the toolbar — so it cannot be derived from the paths alone. Grouping by module puts a
     * repository-root file such as {@code .gitignore} before the files of a nested source module,
     * even though on disk they are siblings; sorting paths produced the opposite, which is what made
     * navigation appear to jump around when crossing a file boundary.
     *
     * <p>Falls back to plain file-tree ordering when the tool window has not built its tree (never
     * opened this session), and appends any changed file the tree does not show, so navigation can
     * never silently skip a file that has gutter markers.
     *
     * <p>Entries that cannot be opened are left out — see {@link #navigable}.
     */
    private List<String> orderedFiles(Map<String, Change> scopeChanges, Map<String, Change> localChanges) {
        java.util.TreeSet<String> changedFiles = new java.util.TreeSet<>(FileTreeOrder.INSTANCE);
        changedFiles.addAll(scopeChanges.keySet());
        changedFiles.addAll(localChanges.keySet());

        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        List<String> displayed = toolWindowService == null
                ? Collections.emptyList()
                : toolWindowService.getDisplayOrderedPaths();
        if (displayed.isEmpty()) {
            LOG.debug("ChangeNavigation: tree order unavailable, ordering by file tree");
            return navigable(changedFiles);
        }

        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        for (String path : displayed) {
            if (changedFiles.contains(path)) ordered.add(path);
        }
        ordered.addAll(changedFiles);
        return navigable(ordered);
    }

    /**
     * Keeps only the entries navigation can actually put a caret in.
     *
     * <p>A scope contains things that are not text a caret can move through:
     * <ul>
     *   <li>a file deleted in the scope, which has no content left to show;</li>
     *   <li>a directory recorded as a change of its own — a submodule, whose changed commit hash
     *       shows up as a change on the checked-out folder, or a folder added or removed;</li>
     *   <li>a binary file, which opens in a viewer with no lines to step through.</li>
     * </ul>
     *
     * <p>These used to stay in the list, and stepping onto one made navigation stop where it stood,
     * because opening resolved nothing and returned without moving; pressing again from the
     * unchanged position then jumped somewhere unrelated.
     *
     * <p>Dropping them here rather than when opening also keeps the index arithmetic honest: "the
     * next file" and the wrap at either end are computed over files that can be reached.
     */
    private List<String> navigable(java.util.Collection<String> paths) {
        List<String> result = new ArrayList<>(paths.size());
        for (String path : paths) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
            if (file != null && file.isValid() && !file.isDirectory() && !isBinary(file)) {
                result.add(path);
            }
        }
        return result;
    }

    /** File type resolution touches the VFS, so never let a failure here abort navigation. */
    private static boolean isBinary(@NotNull VirtualFile file) {
        try {
            return file.getFileType().isBinary();
        } catch (Exception e) {
            LOG.debug("ChangeNavigation: could not determine file type of " + file.getPath(), e);
            return false;
        }
    }

    /** Project-relative path when possible, so the log lines up with the tree. */
    private String displayPath(String path) {
        String base = project.getBasePath();
        if (base != null && path.length() > base.length() + 1 && path.startsWith(base)) {
            return path.substring(base.length() + 1);
        }
        return path;
    }

    // --- small utilities ---

    /** First line strictly greater than {@code line}, or null if none. */
    private static @Nullable Integer firstLineAfter(List<Integer> sortedLines, int line) {
        for (Integer l : sortedLines) {
            if (l > line) return l;
        }
        return null;
    }

    /** Last line strictly less than {@code line}, or null if none. */
    private static @Nullable Integer firstLineBefore(List<Integer> sortedLines, int line) {
        Integer result = null;
        for (Integer l : sortedLines) {
            if (l < line) result = l;
            else break;
        }
        return result;
    }

    private static int wrapIndex(int idx, int size) {
        if (size == 0) return 0;
        return ((idx % size) + size) % size;
    }
}
