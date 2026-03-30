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
        volatile String baseContent;  // Scope base revision content (e.g., HEAD~2)
        volatile String headContent;  // HEAD content (cached for fast filtering)
        volatile Change localChange;  // Local change for filtering
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

            // Check if IDE gutter is enabled to determine rendering strategy
            VcsApplicationSettings vcsSettings = VcsApplicationSettings.getInstance();
            boolean ideGutterEnabled = vcsSettings.SHOW_LST_GUTTER_MARKERS;

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
        final String headContent;  // Cache HEAD content to avoid VCS calls on every keystroke
        final String currentContent;
        final List<Range> precomputedRanges;
        final Change localChange;  // Store local change for future recalculations

        UpdateInfo(Document document, VirtualFile file, String baseContent, String headContent, String currentContent, List<Range> ranges, Change localChange) {
            this.document = document;
            this.file = file;
            this.baseContent = baseContent;
            this.headContent = headContent;
            this.currentContent = currentContent;
            this.precomputedRanges = ranges;
            this.localChange = localChange;
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
            // Compare current document to base (target)
            // This gives us all changes: scope + local combined
            List<Range> allRanges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, normalizedBase);
            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", all ranges computed: " + allRanges.size());

            // If file has local changes, filter/split ranges to show only scope changes
            if (headContent != null) {
                ranges = filterAndSplitRanges(allRanges, normalizedCurrent, headContent, filePath);
            } else {
                ranges = allRanges;
            }

            LOG.info("MyLineStatusTrackerImpl - File: " + filePath + ", final ranges after filtering: " + ranges.size());
            for (Range range : ranges) {
                LOG.info("MyLineStatusTrackerImpl - Range: line1=" + range.getLine1() + ", line2=" + range.getLine2() +
                    ", vcsLine1=" + range.getVcsLine1() + ", vcsLine2=" + range.getVcsLine2() + ", type=" + range.getType());
            }
        } catch (Exception e) {
            LOG.error("Error precomputing ranges for: " + filePath, e);
            ranges = Collections.emptyList();
        }

        // Get the local change for this file (if any) to store for future recalculations
        Change localChange = (localChangesMap != null) ? localChangesMap.get(filePath) : null;
        
        return new UpdateInfo(doc, file, normalizedBase, headContent, normalizedCurrent, ranges, localChange);
    }

    private void applyBatchedUpdates(List<UpdateInfo> updates) {
        for (UpdateInfo update : updates) {
            if (disposing.get()) break;
            updateRendererWithPrecomputedRanges(update.document, update.file, update.baseContent, update.headContent, update.precomputedRanges, update.localChange);
        }
    }

    /**
     * Updates or creates the renderer with precomputed ranges.
     * This is called on EDT with ranges already computed off EDT for better performance.
     */
    private synchronized void updateRendererWithPrecomputedRanges(@NotNull Document document, @NotNull VirtualFile file,
                                                                   @NotNull String baseContent, String headContent, @NotNull List<Range> ranges, Change localChange) {
        if (disposing.get()) return;

        try {
            // Get or create renderer info
            RendererInfo info = renderers.get(document);
            if (info == null) {
                // Create new renderer
                ScopeLineStatusMarkerRenderer renderer = new ScopeLineStatusMarkerRenderer(
                        project,
                        document,
                        file,
                        this
                );
                info = new RendererInfo(renderer, baseContent);
                renderers.put(document, info);

                // Add document listener to track edits
                DocumentListener documentListener = createDocumentListener(document);
                info.documentListener = documentListener;
                document.addDocumentListener(documentListener, this);
            } else {
                // Update base content
                info.baseContent = baseContent;
            }

            // Cache HEAD content and local change for fast recalculations
            info.headContent = headContent;
            info.localChange = localChange;

            // Set the VCS base content so diff viewer can use it
            info.renderer.setVcsBaseContent(info.baseContent);

            // Update the renderer with precomputed ranges
            info.renderer.updateRanges(ranges);

        } catch (Exception e) {
            LOG.error("Error updating line status renderer", e);
        }
    }

    /**
     * Creates a document listener that recalculates ranges on document changes.
     * Recalculates instantly for "live" movement of scope markers as the user types.
     */
    private DocumentListener createDocumentListener(@NotNull Document document) {
        return new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (disposing.get()) return;

                // Recalculate ranges when document changes for instant "live" movement
                // This ensures scope markers adjust immediately as the user types
                RendererInfo info = renderers.get(document);
                if (info != null && info.baseContent != null) {
                    // Schedule recalculation on pooled thread to avoid blocking EDT
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        if (!disposing.get()) {
                            recalculateAndUpdateRangesAsync(document, info);
                        }
                    });
                }
            }
        };
    }

    /**
     * Filters and splits scope change ranges to exclude local-only changes.
     * When a scope range overlaps with a local change, it's split into parts before/after the local change.
     * Uses cached HEAD content for performance (no VCS calls).
     *
     * @param allRanges All ranges from comparing current document to base (scope + local combined)
     * @param currentContent Current document content
     * @param headContent HEAD revision content (cached)
     * @param filePath File path for logging
     * @return Filtered/split list with only scope change ranges
     */
    private List<Range> filterAndSplitRanges(List<Range> allRanges, String currentContent, String headContent, String filePath) {
        LOG.info("MyLineStatusTrackerImpl - filterAndSplitRanges called for: " + filePath);
        
        if (headContent == null) {
            LOG.info("MyLineStatusTrackerImpl - HEAD content is null, returning all ranges");
            return new ArrayList<>(allRanges);
        }
        
        // Compare HEAD to current to identify local-only changes
        List<Range> localOnlyRanges = RangesBuilder.INSTANCE.createRanges(currentContent, headContent);
        LOG.info("MyLineStatusTrackerImpl - Local-only ranges: " + localOnlyRanges.size());
        for (Range localRange : localOnlyRanges) {
            LOG.info("MyLineStatusTrackerImpl - Local range: line1=" + localRange.getLine1() + 
                ", line2=" + localRange.getLine2());
        }
        
        if (localOnlyRanges.isEmpty()) {
            // No local changes, return all scope ranges
            return new ArrayList<>(allRanges);
        }
        
        // Process each scope range, splitting if it overlaps with local changes
        List<Range> result = new ArrayList<>();
        for (Range scopeRange : allRanges) {
            List<Range> splitRanges = splitRangeAroundLocalChanges(scopeRange, localOnlyRanges, filePath);
            result.addAll(splitRanges);
        }
        
        LOG.info("MyLineStatusTrackerImpl - Final ranges count: " + result.size() + 
            " (from " + allRanges.size() + " original ranges)");
        return result;
    }
    
    /**
     * Splits a scope range around local changes.
     * If the scope range doesn't overlap with any local changes, returns it as-is.
     * If it overlaps, returns the parts before/after the local change(s).
     *
     * @param scopeRange The scope range to potentially split
     * @param localRanges List of local-only change ranges
     * @param filePath File path for logging
     * @return List of ranges (may be empty, single original, or multiple split ranges)
     */
    private List<Range> splitRangeAroundLocalChanges(Range scopeRange, List<Range> localRanges, String filePath) {
        List<Range> result = new ArrayList<>();
        
        // Find all local ranges that overlap with this scope range
        List<Range> overlappingLocal = new ArrayList<>();
        for (Range localRange : localRanges) {
            if (rangesOverlap(scopeRange.getLine1(), scopeRange.getLine2(), 
                             localRange.getLine1(), localRange.getLine2())) {
                overlappingLocal.add(localRange);
            }
        }
        
        if (overlappingLocal.isEmpty()) {
            // No overlap, keep the scope range as-is
            result.add(scopeRange);
            return result;
        }
        
        LOG.info("MyLineStatusTrackerImpl - Splitting scope range [" + scopeRange.getLine1() + "-" + 
            scopeRange.getLine2() + "] around " + overlappingLocal.size() + " local changes");
        
        // Sort overlapping local ranges by start line
        overlappingLocal.sort(Comparator.comparingInt(Range::getLine1));
        
        int currentLine = scopeRange.getLine1();
        int scopeEnd = scopeRange.getLine2();
        int currentVcsLine = scopeRange.getVcsLine1();
        int scopeVcsEnd = scopeRange.getVcsLine2();
        
        // Determine if this is an INSERTED range (green) - no VCS lines
        boolean isInserted = (scopeRange.getVcsLine1() == scopeRange.getVcsLine2());
        
        for (Range localRange : overlappingLocal) {
            int localStart = localRange.getLine1();
            int localEnd = localRange.getLine2();
            
            // Add range before the local change (if any)
            if (currentLine < localStart) {
                Range beforeRange;
                if (isInserted) {
                    // For INSERTED ranges, keep vcsLine1 == vcsLine2 to maintain green color
                    beforeRange = new Range(currentLine, localStart, currentVcsLine, currentVcsLine);
                } else {
                    // For MODIFIED/DELETED ranges, increment VCS lines proportionally
                    int linesInSegment = localStart - currentLine;
                    int totalCurrentLines = scopeEnd - scopeRange.getLine1();
                    int totalVcsLines = scopeVcsEnd - scopeRange.getVcsLine1();
                    int vcsLinesInSegment = (totalVcsLines * linesInSegment) / totalCurrentLines;
                    beforeRange = new Range(currentLine, localStart, currentVcsLine, currentVcsLine + vcsLinesInSegment);
                    currentVcsLine += vcsLinesInSegment;
                }
                result.add(beforeRange);
                LOG.info("MyLineStatusTrackerImpl - Added before-segment: [" + currentLine + "-" + localStart + "], VCS: [" + 
                    beforeRange.getVcsLine1() + "-" + beforeRange.getVcsLine2() + "], type: " + beforeRange.getType());
            }
            
            // Skip the local change area
            currentLine = localEnd;
        }
        
        // Add remaining range after last local change (if any)
        if (currentLine < scopeEnd) {
            Range afterRange;
            if (isInserted) {
                // For INSERTED ranges, keep vcsLine1 == vcsLine2 to maintain green color
                afterRange = new Range(currentLine, scopeEnd, currentVcsLine, currentVcsLine);
            } else {
                // For MODIFIED/DELETED ranges, use remaining VCS lines
                afterRange = new Range(currentLine, scopeEnd, currentVcsLine, scopeVcsEnd);
            }
            result.add(afterRange);
            LOG.info("MyLineStatusTrackerImpl - Added after-segment: [" + currentLine + "-" + scopeEnd + "], VCS: [" + 
                afterRange.getVcsLine1() + "-" + afterRange.getVcsLine2() + "], type: " + afterRange.getType());
        }
        
        LOG.info("MyLineStatusTrackerImpl - Split scope range into " + result.size() + " segments");
        return result;
    }

    /**
     * Filters out local-only changes from the combined ranges.
     * We want to show only scope changes (HEAD~2 → HEAD), not local changes (HEAD → current).
     *
     * @param allRanges All ranges from comparing current document to base (scope + local combined)
     * @param currentContent Current document content
     * @param baseContent Base revision content (e.g., HEAD~2)
     * @param localChange The local change object (HEAD → current)
     * @param filePath File path for logging
     * @return Filtered list with only scope change ranges
     */
    private List<Range> filterOutLocalChanges(List<Range> allRanges, String currentContent, String baseContent,
                                               Change localChange, String filePath) {
        LOG.info("MyLineStatusTrackerImpl - filterOutLocalChanges called for: " + filePath);
        
        if (localChange == null || localChange.getBeforeRevision() == null) {
            LOG.info("MyLineStatusTrackerImpl - No local change or beforeRevision, returning all ranges");
            return new ArrayList<>(allRanges);
        }
        
        try {
            // Get HEAD content (beforeRevision of localChange represents HEAD)
            String headContent = localChange.getBeforeRevision().getContent();
            if (headContent == null) {
                LOG.warn("MyLineStatusTrackerImpl - HEAD content is null, returning all ranges");
                return new ArrayList<>(allRanges);
            }
            
            String normalizedHead = StringUtil.convertLineSeparators(headContent);
            LOG.info("MyLineStatusTrackerImpl - HEAD content lines: " + normalizedHead.split("\n").length);
            
            // Compare HEAD to current to identify local-only changes
            List<Range> localOnlyRanges = RangesBuilder.INSTANCE.createRanges(currentContent, normalizedHead);
            LOG.info("MyLineStatusTrackerImpl - Local-only ranges: " + localOnlyRanges.size());
            for (Range localRange : localOnlyRanges) {
                LOG.info("MyLineStatusTrackerImpl - Local range: line1=" + localRange.getLine1() + 
                    ", line2=" + localRange.getLine2() + 
                    ", vcsLine1=" + localRange.getVcsLine1() + 
                    ", vcsLine2=" + localRange.getVcsLine2());
            }
            
            // Filter out ranges that overlap with local-only changes
            List<Range> filteredRanges = new ArrayList<>();
            for (Range range : allRanges) {
                boolean overlapsWithLocal = false;
                
                for (Range localRange : localOnlyRanges) {
                    // Check if ranges overlap in the current document
                    // range uses line1/line2 for current document lines
                    // localRange uses line1/line2 for current document lines
                    if (rangesOverlap(range.getLine1(), range.getLine2(), 
                                     localRange.getLine1(), localRange.getLine2())) {
                        overlapsWithLocal = true;
                        LOG.info("MyLineStatusTrackerImpl - Range overlaps with local: " +
                            "range[" + range.getLine1() + "-" + range.getLine2() + "] overlaps with " +
                            "local[" + localRange.getLine1() + "-" + localRange.getLine2() + "]");
                        break;
                    }
                }
                
                if (!overlapsWithLocal) {
                    filteredRanges.add(range);
                } else {
                    LOG.info("MyLineStatusTrackerImpl - Filtering out range: line1=" + range.getLine1() + 
                        ", line2=" + range.getLine2() + " (overlaps with local change)");
                }
            }
            
            LOG.info("MyLineStatusTrackerImpl - Filtered ranges count: " + filteredRanges.size() + 
                " (removed " + (allRanges.size() - filteredRanges.size()) + " local-only ranges)");
            return filteredRanges;
            
        } catch (VcsException e) {
            LOG.warn("MyLineStatusTrackerImpl - Error getting HEAD content: " + e.getMessage(), e);
            return new ArrayList<>(allRanges);
        }
    }
    
    /**
     * Checks if two line ranges overlap.
     * Ranges are considered overlapping if they share any common lines.
     *
     * @param start1 Start line of first range (inclusive)
     * @param end1 End line of first range (exclusive)
     * @param start2 Start line of second range (inclusive)
     * @param end2 End line of second range (exclusive)
     * @return true if ranges overlap
     */
    private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
        // Check if ranges don't overlap, then negate
        // No overlap if: range1 ends before range2 starts OR range2 ends before range1 starts
        return !(end1 <= start2 || end2 <= start1);
    }

    /**
     * Recalculates ranges asynchronously (off EDT) and updates renderer on EDT.
     * Applies filtering/splitting if local changes exist to show only scope changes.
     * Uses cached HEAD content for performance (no VCS calls).
     */
    private void recalculateAndUpdateRangesAsync(@NotNull Document document, @NotNull RendererInfo info) {
        try {
            // Get current document content (ReadAction can be called from any thread)
            String currentContent = com.intellij.openapi.application.ReadAction.compute(() ->
                document.getImmutableCharSequence().toString()
            );
            String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);
            
            // Compute all ranges by comparing current document with base revision
            // This computation is done off EDT for better performance
            List<Range> allRanges = RangesBuilder.INSTANCE.createRanges(
                    normalizedCurrent,
                    info.baseContent
            );

            // Apply filtering/splitting if we have cached HEAD content
            List<Range> filteredRanges;
            if (info.headContent != null) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                String filePath = (file != null) ? file.getPath() : "unknown";
                // Use cached HEAD content for fast filtering (no VCS call)
                filteredRanges = filterAndSplitRanges(allRanges, normalizedCurrent, info.headContent, filePath);
            } else {
                filteredRanges = allRanges;
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
            // Remove document listener
            if (info.documentListener != null) {
                try {
                    document.removeDocumentListener(info.documentListener);
                } catch (Exception e) {
                    LOG.warn("Error removing document listener", e);
                }
            }

            // Dispose renderer
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
