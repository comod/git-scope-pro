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
        if (scopeChanges == null || scopeChanges.isEmpty()) {
            LOG.debug("ChangeNavigation: no scope changes");
            return;
        }

        // Ordered, stable list of changed files (path order, matching what the user scans).
        List<String> files = new ArrayList<>(scopeChanges.keySet());
        Collections.sort(files);

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
            case NEXT_CHANGE -> navigateChange(files, scopeChanges, fromFile, fromLine, true);
            case PREVIOUS_CHANGE -> navigateChange(files, scopeChanges, fromFile, fromLine, false);
            case NEXT_FILE -> navigateFile(files, scopeChanges, fromFile, true);
            case PREVIOUS_FILE -> navigateFile(files, scopeChanges, fromFile, false);
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
                                @Nullable String currentFilePath, int caretLine, boolean forward) {
        int fileIdx = currentFilePath == null ? -1 : files.indexOf(currentFilePath);

        if (fileIdx >= 0) {
            List<Integer> lines = changeStartLines(currentFilePath, scopeChanges.get(currentFilePath));
            Integer target = forward ? firstLineAfter(lines, caretLine) : firstLineBefore(lines, caretLine);
            if (target != null) {
                open(currentFilePath, target);
                return;
            }
            // Past the last/first change of this file -> move to the adjacent file.
            int nextIdx = wrapIndex(fileIdx + (forward ? 1 : -1), files.size());
            openFileAtEdge(files, scopeChanges, nextIdx, forward);
            return;
        }

        // No current file (or caret not in a changed file): jump to the first/last change overall.
        int startIdx = forward ? 0 : files.size() - 1;
        openFileAtEdge(files, scopeChanges, startIdx, forward);
    }

    // --- File-level navigation (cycle between changed files) ---

    private void navigateFile(List<String> files, Map<String, Change> scopeChanges,
                              @Nullable String currentFilePath, boolean forward) {
        int fileIdx = currentFilePath == null ? -1 : files.indexOf(currentFilePath);
        int targetIdx;
        if (fileIdx < 0) {
            targetIdx = forward ? 0 : files.size() - 1;
        } else {
            targetIdx = wrapIndex(fileIdx + (forward ? 1 : -1), files.size());
        }
        // Always land on the first change of the target file when cycling files.
        openFileAtEdge(files, scopeChanges, targetIdx, true);
    }

    /**
     * Opens the file at {@code files[idx]} placing the caret on its first change (when moving
     * forward) or last change (when moving backward). Falls back to line 0 if ranges can't be
     * computed.
     */
    private void openFileAtEdge(List<String> files, Map<String, Change> scopeChanges, int idx, boolean firstChange) {
        if (idx < 0 || idx >= files.size()) return;
        String path = files.get(idx);
        List<Integer> lines = changeStartLines(path, scopeChanges.get(path));
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
        lastNavigatedFile = path;
        lastNavigatedLine = line;
        // Request focus so the opened editor becomes the focus owner and the frontend reports the
        // correct caret position on the next navigation keystroke.
        FileOpener.openAndGoToLine(project, file, line, true);
        highlightInToolWindow(file);
    }

    /** Selects/highlights the file's change in the Git Scope tool window tree, so the tree stays in sync. */
    private void highlightInToolWindow(@NotNull VirtualFile file) {
        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
        if (toolWindowService == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            toolWindowService.selectFile(file);
        });
    }

    // --- Range helpers ---

    /**
     * Returns the sorted 0-based start lines of all changes in the given file.
     *
     * <p>Prefers the ranges already cached by the gutter pipeline (exact match with the visible
     * markers) for open files; otherwise computes base-vs-current ranges on demand so navigation
     * works for files that have not been opened yet.
     */
    private List<Integer> changeStartLines(String path, @Nullable Change change) {
        GutterDataService gds = project.getService(GutterDataService.class);
        if (gds != null) {
            GutterDataService.GutterFileData cached = gds.getData(path);
            if (cached != null && cached.ranges != null && !cached.ranges.isEmpty()) {
                return sortedStartLines(cached.ranges);
            }
        }
        List<Range> computed = computeRanges(path, change);
        return sortedStartLines(computed);
    }

    private static List<Integer> sortedStartLines(List<Range> ranges) {
        List<Integer> lines = new ArrayList<>(ranges.size());
        for (Range r : ranges) lines.add(r.getLine1());
        Collections.sort(lines);
        return lines;
    }

    /**
     * Computes base-vs-current change ranges for a file not covered by cached gutter data.
     * Runs off the EDT concerns by reading content under a read action.
     */
    private List<Range> computeRanges(String path, @Nullable Change change) {
        if (change == null || change.getBeforeRevision() == null) return Collections.emptyList();
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) return Collections.emptyList();

        String baseContent;
        try {
            baseContent = change.getBeforeRevision().getContent();
        } catch (VcsException e) {
            LOG.warn("ChangeNavigation: error getting base content for " + path, e);
            return Collections.emptyList();
        }
        if (baseContent == null) return Collections.emptyList();

        String currentContent = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Document doc = FileDocumentManager.getInstance().getDocument(file);
            return doc != null ? doc.getImmutableCharSequence().toString() : null;
        });
        if (currentContent == null) return Collections.emptyList();

        String normalizedBase = StringUtil.convertLineSeparators(baseContent);
        String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);
        try {
            return RangesBuilder.INSTANCE.createRanges(normalizedCurrent, normalizedBase);
        } catch (Exception e) {
            LOG.warn("ChangeNavigation: error computing ranges for " + path, e);
            return Collections.emptyList();
        }
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
