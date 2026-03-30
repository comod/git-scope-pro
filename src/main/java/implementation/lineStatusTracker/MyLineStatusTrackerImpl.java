package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import implementation.gutter.Range;
import implementation.gutter.RangesBuilder;
import implementation.gutter.ScopeLineStatusMarkerRenderer;
import org.jetbrains.annotations.NotNull;
import system.Defs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages custom line status markers for scope changes in editor gutters.
 * Uses custom gutter painting instead of modifying IntelliJ's internal line status tracker.
 */
public class MyLineStatusTrackerImpl implements Disposable {
    private static final Logger LOG = Defs.getLogger(MyLineStatusTrackerImpl.class);

    private final Project project;
    private MessageBusConnection messageBusConnection;
    private final AtomicBoolean disposing = new AtomicBoolean(false);

    // Lightweight disposable token to check disposal state without capturing 'this'
    private static class DisposalToken {
        volatile boolean disposed = false;
    }
    private final DisposalToken disposalToken = new DisposalToken();

    // Track per-document renderers and base content
    private final Map<Document, RendererInfo> renderers = new HashMap<>();

    private static final class RendererInfo {
        volatile ScopeLineStatusMarkerRenderer renderer;
        volatile String baseContent;          // Scope base revision content (e.g., HEAD~2)
        volatile String headContent;          // HEAD content (cached for fast filtering)
        volatile List<Range> scopeRanges;     // diff(HEAD, scopeBase) — stable between keystrokes
        volatile DocumentListener documentListener;

        RendererInfo(ScopeLineStatusMarkerRenderer renderer, String baseContent) {
            this.renderer = renderer;
            this.baseContent = baseContent;
        }
    }

    @Override
    public void dispose() {
        disposalToken.disposed = true;
        releaseAll();
    }

    public MyLineStatusTrackerImpl(Project project, Disposable parentDisposable) {
        this.project = project;

        MessageBus messageBus = project.getMessageBus();
        this.messageBusConnection = messageBus.connect();

        // Listen to file open/close events
        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile virtualFile) {
                        // Renderers are created on-demand when changes are detected
                    }

                    @Override
                    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        Document doc = FileDocumentManager.getInstance().getDocument(file);
                        if (doc != null) {
                            release(doc);
                        }
                    }
                }
        );

        Disposer.register(parentDisposable, this);
    }

    private boolean isDiffView(Editor editor) {
        return editor.getEditorKind() == EditorKind.DIFF;
    }

    /**
     * Updates line status markers for all editors based on scope changes.
     *
     * @param scopeChangesMap Map of file path to Change (scope or merged changes)
     * @param localChangesMap Map of file path to Change (local changes only, may be null)
     */
    public void update(Map<String, Change> scopeChangesMap, Map<String, Change> localChangesMap) {
        if (scopeChangesMap == null || disposing.get()) {
            return;
        }

        final DisposalToken token = this.disposalToken;
        // Process editors in parallel on background threads
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (token.disposed) return;

            Editor[] editors = EditorFactory.getInstance().getAllEditors();

            // Filter editors that need updates
            List<Editor> editorsToUpdate = new ArrayList<>();
            for (Editor editor : editors) {
                if (isDiffView(editor)) continue;

                Document doc = editor.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                if (file == null) continue;

                String filePath = file.getPath();

                // Process if file has scope changes OR we already have a renderer for it (to clear)
                if (scopeChangesMap.containsKey(filePath) || renderers.containsKey(doc)) {
                    editorsToUpdate.add(editor);
                }
            }

            if (editorsToUpdate.isEmpty()) return;

            // Process all editors in parallel
            Map<Document, UpdateInfo> updates = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(editorsToUpdate.size());

            for (Editor editor : editorsToUpdate) {
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        if (!token.disposed) {
                            UpdateInfo updateInfo = prepareUpdateForEditor(editor, scopeChangesMap, localChangesMap);
                            if (updateInfo != null) {
                                updates.put(updateInfo.document, updateInfo);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all parallel tasks to complete
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Batch all EDT updates into a single invokeLater call
            if (!updates.isEmpty() && !token.disposed) {
                List<UpdateInfo> updateList = new ArrayList<>(updates.values());
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!token.disposed) {
                        applyBatchedUpdates(updateList);
                    }
                }, ModalityState.defaultModalityState(), __ -> token.disposed);
            }
        });
    }

    private static class UpdateInfo {
        final Document document;
        final VirtualFile file;
        final String baseContent;
        final String headContent;   // Cached HEAD content (avoids VCS calls on every keystroke)
        final List<Range> precomputedRanges;

        UpdateInfo(Document document, VirtualFile file, String baseContent, String headContent, List<Range> ranges) {
            this.document = document;
            this.file = file;
            this.baseContent = baseContent;
            this.headContent = headContent;
            this.precomputedRanges = ranges;
        }
    }

    private UpdateInfo prepareUpdateForEditor(Editor editor, Map<String, Change> scopeChangesMap, Map<String, Change> localChangesMap) {
        if (editor == null || disposing.get()) return null;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return null;

        String filePath = file.getPath();
        Change changeForFile = scopeChangesMap.get(filePath);

        // Determine if file has local changes - we'll filter them out from ranges
        boolean hasLocalChanges = (localChangesMap != null && localChangesMap.containsKey(filePath));

        String baseContent;
        String currentContent;

        if (changeForFile != null && changeForFile.getBeforeRevision() != null) {
            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", beforeRevision: " +
                (changeForFile.getBeforeRevision() != null ? changeForFile.getBeforeRevision().getRevisionNumber() : "null") +
                ", afterRevision: " +
                (changeForFile.getAfterRevision() != null ? changeForFile.getAfterRevision().getRevisionNumber() : "null"));

            // Extract base content (target revision - e.g., HEAD~2)
            try {
                baseContent = changeForFile.getBeforeRevision().getContent();
                LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", beforeRevision lines: " + (baseContent != null ? baseContent.split("\n").length : "null"));
            } catch (VcsException e) {
                LOG.warn("Error getting content for revision: " + filePath, e);
                baseContent = null;
            }

            if (baseContent == null) {
                baseContent = com.intellij.openapi.application.ReadAction.compute(() -> doc.getCharsSequence().toString());
            }

            // Always use current document content for comparison
            // This is what's actually displayed in the editor
            currentContent = com.intellij.openapi.application.ReadAction.compute(() ->
                doc.getImmutableCharSequence().toString()
            );
            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", current document lines: " + currentContent.split("\n").length);

        } else {
            // No scope change for this file - clear markers by using current content as base
            baseContent = com.intellij.openapi.application.ReadAction.compute(() -> doc.getCharsSequence().toString());
            currentContent = baseContent;  // No diff
            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", no scope change - clearing markers");
        }

        // Precompute ranges off EDT
        String normalizedBase = StringUtil.convertLineSeparators(baseContent);
        String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);

        LOG.info("MyLineStatusTrackerImpl - File: " + filePath +
            ", normalizedBase lines: " + normalizedBase.split("\n").length +
            ", normalizedCurrent lines: " + normalizedCurrent.split("\n").length +
            ", hasLocalChanges: " + hasLocalChanges);

        // Extract and cache HEAD content if we have local changes (for fast filtering)
        String headContent = null;
        if (hasLocalChanges && localChangesMap != null) {
            Change localChange = localChangesMap.get(filePath);
            if (localChange != null && localChange.getBeforeRevision() != null) {
                try {
                    headContent = localChange.getBeforeRevision().getContent();
                    if (headContent != null) {
                        headContent = StringUtil.convertLineSeparators(headContent);
                        LOG.info("MyLineStatusTrackerImpl - Cached HEAD content, lines: " + headContent.split("\n").length);
                    }
                } catch (VcsException e) {
                    LOG.warn("MyLineStatusTrackerImpl - Error caching HEAD content: " + e.getMessage());
                }
            }
        }

        List<Range> ranges;
        try {
            if (headContent != null) {
                // Use HEAD as bridge: diff(HEAD, scopeBase) gives exact VCS coords,
                // then map to current space using diff(current, HEAD)
                ranges = computeScopeRangesInCurrentSpace(headContent, normalizedBase, normalizedCurrent, filePath);
            } else {
                // No local change info available: direct diff against scope base
                ranges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, normalizedBase);
            }

            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", final ranges: " + ranges.size());
            for (Range range : ranges) {
                LOG.info("MyLineStatusTrackerImpl - Range: line1=" + range.getLine1() + ", line2=" + range.getLine2() +
                    ", vcsLine1=" + range.getVcsLine1() + ", vcsLine2=" + range.getVcsLine2() + ", type=" + range.getType());
            }
        } catch (Exception e) {
            LOG.error("Error precomputing ranges for: " + filePath, e);
            ranges = Collections.emptyList();
        }

        return new UpdateInfo(doc, file, normalizedBase, headContent, ranges);
    }

    private void applyBatchedUpdates(List<UpdateInfo> updates) {
        for (UpdateInfo update : updates) {
            if (disposing.get()) break;
            updateRendererWithPrecomputedRanges(update.document, update.file, update.baseContent, update.headContent, update.precomputedRanges);
        }
    }

    /**
     * Updates or creates the renderer with precomputed ranges.
     * Called on EDT; ranges were already computed off EDT for performance.
     */
    private synchronized void updateRendererWithPrecomputedRanges(
            @NotNull Document document, @NotNull VirtualFile file,
            @NotNull String baseContent, String headContent, @NotNull List<Range> ranges) {
        if (disposing.get()) return;

        try {
            RendererInfo info = renderers.get(document);
            if (info == null) {
                ScopeLineStatusMarkerRenderer renderer = new ScopeLineStatusMarkerRenderer(
                        project, document, file, this);
                info = new RendererInfo(renderer, baseContent);
                renderers.put(document, info);

                DocumentListener documentListener = createDocumentListener(document, info);
                info.documentListener = documentListener;
                document.addDocumentListener(documentListener, this);
            } else {
                info.baseContent = baseContent;
            }

            info.headContent = headContent;

            // Pre-compute diff(HEAD, scopeBase) once — both are fixed git revisions and
            // never change while the user types, so keystrokes only need diff(current, HEAD).
            if (headContent != null) {
                info.scopeRanges = RangesBuilder.INSTANCE.createRanges(headContent, baseContent);
            } else {
                info.scopeRanges = null;
            }

            // Let the diff viewer access the VCS base content for popups/rollback
            info.renderer.setVcsBaseContent(info.baseContent);

            info.renderer.updateRanges(ranges);

        } catch (Exception e) {
            LOG.error("Error updating line status renderer", e);
        }
    }

    /**
     * Creates a document listener that recalculates scope ranges on every document change.
     * Runs off EDT so it does not block typing.
     */
    private DocumentListener createDocumentListener(@NotNull Document document, @NotNull RendererInfo info) {
        return new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (disposing.get()) return;
                if (info.baseContent == null) return;

                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    if (!disposing.get()) {
                        recalculateAndUpdateRangesAsync(document, info);
                    }
                });
            }
        };
    }

    /**
     * Computes scope ranges in current-document coordinate space using HEAD as a bridge.
     *
     * <p>Instead of diff(current, scopeBase) and stripping local changes (which conflates
     * scope and local edits in the same diff block and requires error-prone proportional VCS
     * estimation), this method:
     * <ol>
     *   <li>Computes diff(HEAD, scopeBase) → pure scope ranges with <em>exact</em> VCS coords
     *   <li>Computes diff(current, HEAD)   → local change ranges (HEAD-space line numbers)
     *   <li>Maps scope ranges from HEAD space into current-doc space, suppressing or splitting
     *       any sub-segments that overlap with local changes
     * </ol>
     *
     * <p>Benefits over the old approach:
     * <ul>
     *   <li>VCS coordinates are always exact for non-split ranges
     *   <li>Current-doc line positions are exact (cumulative-delta, not proportional)
     *   <li>Scope and local changes are never conflated in the same diff block
     *   <li>Partial-overlap is handled correctly: e.g. a scope range covering lines 1-4
     *       where the user edits only line 4 will continue to show lines 1-3 in the gutter
     * </ul>
     */
    private List<Range> computeScopeRangesInCurrentSpace(
            String headContent,
            String normalizedBase,
            String currentContent,
            String filePath) {

        // Step 1: pure scope changes — diff(HEAD, scopeBase)
        //   line1/line2       = HEAD lines
        //   vcsLine1/vcsLine2 = scopeBase lines  (EXACT VCS coordinates)
        List<Range> scopeRanges = RangesBuilder.INSTANCE.createRanges(headContent, normalizedBase);
        LOG.info("computeScopeRangesInCurrentSpace [" + filePath + "] scope ranges: " + scopeRanges.size());

        if (scopeRanges.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: local changes — diff(current, HEAD)
        //   line1/line2       = current-doc lines
        //   vcsLine1/vcsLine2 = HEAD lines
        List<Range> localRanges = RangesBuilder.INSTANCE.createRanges(currentContent, headContent);
        LOG.info("computeScopeRangesInCurrentSpace [" + filePath + "] local ranges: " + localRanges.size());

        if (localRanges.isEmpty()) {
            // HEAD == current: scope ranges are already valid in current-doc space
            return new ArrayList<>(scopeRanges);
        }

        // Step 3: map scope ranges from HEAD space to current-doc space
        return mapScopeRangesToCurrentSpace(scopeRanges, localRanges, filePath);
    }

    /**
     * Translates scope ranges from HEAD coordinate space into current-document space,
     * splitting or suppressing any portions that overlap with local changes.
     *
     * @param scopeRanges  from diff(HEAD, scopeBase):  line1/2=HEAD,    vcsLine1/2=scopeBase
     * @param localRanges  from diff(current, HEAD):    line1/2=current, vcsLine1/2=HEAD
     */
    private List<Range> mapScopeRangesToCurrentSpace(
            List<Range> scopeRanges,
            List<Range> localRanges,
            String filePath) {

        List<Range> result = new ArrayList<>();
        int numLocals = localRanges.size();

        // cumulativeDelta: for HEAD lines not inside any local change,
        //   currentLine = headLine + cumulativeDelta
        int cumulativeDelta = 0;
        int localIdx = 0;

        for (Range scope : scopeRanges) {
            int headStart = scope.getLine1();
            int headEnd   = scope.getLine2();
            int vcsStart  = scope.getVcsLine1();
            int vcsEnd    = scope.getVcsLine2();

            // Advance cumulativeDelta past all locals that end completely before headStart
            while (localIdx < numLocals && localRanges.get(localIdx).getVcsLine2() <= headStart) {
                Range local = localRanges.get(localIdx);
                cumulativeDelta += (local.getLine2() - local.getLine1())
                                 - (local.getVcsLine2() - local.getVcsLine1());
                localIdx++;
            }

            // ── DELETED scope range (0 HEAD lines, >0 VCS lines) ─────────────────
            // A point in HEAD where scopeBase lines were removed; rendered as a
            // deletion marker between two gutter lines.
            if (headStart == headEnd) {
                boolean insideLocal = false;
                for (int i = localIdx; i < numLocals; i++) {
                    Range local = localRanges.get(i);
                    if (local.getVcsLine1() > headStart) break;          // sorted; done
                    if (local.getVcsLine2() > headStart) { insideLocal = true; break; }
                }
                if (!insideLocal) {
                    int pos = headStart + cumulativeDelta;
                    result.add(new Range(pos, pos, vcsStart, vcsEnd));
                    LOG.info("mapScopeRangesToCurrentSpace [" + filePath
                            + "] DELETED at current=" + pos + " vcs=[" + vcsStart + "-" + vcsEnd + "]");
                }
                continue;
            }

            // ── INSERTED / MODIFIED scope range ──────────────────────────────────
            int tempLocalIdx = localIdx;
            int headCursor;
            int currentCursor;

            // If a local change straddles headStart (started before, ends after),
            // the scope range's beginning falls inside a local edit — skip past it.
            if (tempLocalIdx < numLocals) {
                Range straddle = localRanges.get(tempLocalIdx);
                if (straddle.getVcsLine1() < headStart && straddle.getVcsLine2() > headStart) {
                    headCursor    = straddle.getVcsLine2();
                    currentCursor = straddle.getLine2();
                    tempLocalIdx++;
                } else {
                    headCursor    = headStart;
                    currentCursor = headStart + cumulativeDelta;
                }
            } else {
                headCursor    = headStart;
                currentCursor = headStart + cumulativeDelta;
            }

            while (headCursor < headEnd) {
                // Find the next local change that starts before headEnd
                Range nextLocal = null;
                if (tempLocalIdx < numLocals) {
                    Range candidate = localRanges.get(tempLocalIdx);
                    if (candidate.getVcsLine1() < headEnd) nextLocal = candidate;
                }

                if (nextLocal != null) {
                    int localHeadStart = nextLocal.getVcsLine1();
                    int localHeadEnd   = nextLocal.getVcsLine2();

                    // Emit the clean scope segment that precedes this local change
                    if (localHeadStart > headCursor) {
                        emitScopeSegment(result,
                                headCursor, localHeadStart,
                                currentCursor,
                                headStart, headEnd, vcsStart, vcsEnd, filePath);
                        currentCursor += (localHeadStart - headCursor);
                    }

                    // Suppress the portion of the scope range overlapped by the local change
                    headCursor    = Math.max(headCursor, localHeadEnd);
                    currentCursor = nextLocal.getLine2();
                    tempLocalIdx++;

                } else {
                    // No more overlapping local changes — emit the rest of the scope range
                    emitScopeSegment(result,
                            headCursor, headEnd,
                            currentCursor,
                            headStart, headEnd, vcsStart, vcsEnd, filePath);
                    headCursor = headEnd;
                }
            }
        }

        LOG.info("mapScopeRangesToCurrentSpace [" + filePath
                + "] " + scopeRanges.size() + " scope → " + result.size() + " result ranges");
        return result;
    }

    /**
     * Emits one (possibly split) sub-segment of a scope range into {@code result}.
     *
     * <p>When the full scope range is emitted unmodified, the VCS coordinates are exact
     * (inherited from diff(HEAD, scopeBase)).
     *
     * <p>When a MODIFIED scope block is split by a local change, the VCS coordinates of
     * the sub-segment are estimated proportionally within the block.  This is a bounded
     * approximation — the split point is correct at both block boundaries, and the error
     * cannot exceed the VCS extent of the block.
     */
    private void emitScopeSegment(
            List<Range> result,
            int headSegStart, int headSegEnd,     // HEAD lines for this segment
            int currentStart,                     // current-doc start (exact, delta-based)
            int headBlockStart, int headBlockEnd, // full scope block HEAD extents
            int vcsBlockStart, int vcsBlockEnd,   // full scope block VCS extents
            String filePath) {

        int currentEnd  = currentStart + (headSegEnd - headSegStart);
        int headBlockLen = headBlockEnd - headBlockStart;

        int segVcsStart;
        int segVcsEnd;

        if (headBlockLen == 0) {
            // INSERTED block: no HEAD lines, no splitting possible — use full VCS extent
            segVcsStart = vcsBlockStart;
            segVcsEnd   = vcsBlockEnd;
        } else {
            long vcsLen = vcsBlockEnd - vcsBlockStart;
            segVcsStart = vcsBlockStart + (int)(vcsLen * (headSegStart - headBlockStart) / headBlockLen);
            segVcsEnd   = vcsBlockStart + (int)(vcsLen * (headSegEnd   - headBlockStart) / headBlockLen);
        }

        if (currentStart < currentEnd || segVcsStart < segVcsEnd) {
            result.add(new Range(currentStart, currentEnd, segVcsStart, segVcsEnd));
            LOG.info("emitScopeSegment [" + filePath
                    + "] current=[" + currentStart + "-" + currentEnd
                    + "] vcs=[" + segVcsStart + "-" + segVcsEnd + "]");
        }
    }

    /**
     * Recalculates ranges asynchronously (off EDT) and updates renderer on EDT.
     * Applies filtering/splitting if local changes exist to show only scope changes.
     * Uses cached HEAD content for performance (no VCS calls).
     */
    private void recalculateAndUpdateRangesAsync(@NotNull Document document, @NotNull RendererInfo info) {
        try {
            // Respect the IDE gutter setting: if it's disabled, clear any displayed markers
            if (!VcsApplicationSettings.getInstance().SHOW_LST_GUTTER_MARKERS) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposing.get() && info.renderer != null) {
                        info.renderer.updateRanges(Collections.emptyList());
                    }
                }, ModalityState.defaultModalityState());
                return;
            }

            // Get current document content (ReadAction can be called from any thread)
            String currentContent = com.intellij.openapi.application.ReadAction.compute(() ->
                document.getImmutableCharSequence().toString()
            );
            String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);
            
            VirtualFile file = FileDocumentManager.getInstance().getFile(document);
            String filePath = (file != null) ? file.getPath() : "unknown";

            List<Range> filteredRanges;
            if (info.headContent != null && info.scopeRanges != null) {
                // Fast path: diff(HEAD, scopeBase) is cached; only diff(current, HEAD) + mapping runs per keystroke.
                List<Range> localRanges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, info.headContent);
                if (localRanges.isEmpty()) {
                    filteredRanges = new ArrayList<>(info.scopeRanges);
                } else {
                    filteredRanges = mapScopeRangesToCurrentSpace(info.scopeRanges, localRanges, filePath);
                }
            } else {
                filteredRanges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, info.baseContent);
            }

            // Update the renderer on EDT
            final List<Range> rangesToApply = filteredRanges;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!disposing.get() && info.renderer != null) {
                    info.renderer.updateRanges(rangesToApply);
                }
            }, ModalityState.defaultModalityState());

        } catch (Exception e) {
            LOG.error("Error recalculating ranges", e);
        }
    }

    /**
     * Release renderer for a specific document.
     */
    private synchronized void release(@NotNull Document document) {
        RendererInfo info = renderers.remove(document);
        if (info != null) {
            if (info.documentListener != null) {
                try {
                    document.removeDocumentListener(info.documentListener);
                } catch (Exception e) {
                    LOG.warn("Error removing document listener", e);
                }
            }

            if (info.renderer != null) {
                try {
                    info.renderer.dispose();
                } catch (Exception e) {
                    LOG.warn("Error disposing renderer for document", e);
                }
            }
        }
    }

    /**
     * Release all renderers.
     */
    public void releaseAll() {
        if (!disposing.compareAndSet(false, true)) {
            return; // already disposing
        }

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }

        Runnable release = () -> {
            for (Map.Entry<Document, RendererInfo> entry : renderers.entrySet()) {
                RendererInfo info = entry.getValue();
                if (info != null && info.renderer != null) {
                    try {
                        info.renderer.dispose();
                    } catch (Exception e) {
                        LOG.warn("Error disposing renderer", e);
                    }
                }
            }
            renderers.clear();
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            release.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(release);
        }

        // Null out references to prevent retention
        messageBusConnection = null;
    }
}
