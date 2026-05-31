package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.messages.MessageBusConnection;
import implementation.gutter.Range;
import implementation.gutter.RangesBuilder;
import org.jetbrains.annotations.NotNull;
import service.GutterDataService;
import system.Defs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backend-only service that computes line-level diff ranges for scope changes
 * and publishes them to {@link GutterDataService} for rendering on the frontend.
 */
public class MyLineStatusTrackerImpl implements Disposable {
    private static final Logger LOG = Defs.getLogger(MyLineStatusTrackerImpl.class);

    private final Project project;
    private final GutterDataService gutterDataService;
    private MessageBusConnection messageBusConnection;
    private final AtomicBoolean disposing = new AtomicBoolean(false);
    private final ExecutorService updateExecutor =
            SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ScopeGutterUpdate");
    private final AtomicLong updateGeneration = new AtomicLong(0);

    private static class DisposalToken {
        volatile boolean disposed = false;
    }
    private final DisposalToken disposalToken = new DisposalToken();

    // Track which documents we've published data for (so we can clear them)
    private final Set<String> publishedFiles = ConcurrentHashMap.newKeySet();

    @Override
    public void dispose() {
        disposalToken.disposed = true;
        updateExecutor.shutdownNow();
        releaseAll();
    }

    public MyLineStatusTrackerImpl(Project project, Disposable parentDisposable) {
        this.project = project;
        this.gutterDataService = project.getService(GutterDataService.class);

        this.messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        String path = file.getPath();
                        if (publishedFiles.remove(path)) {
                            gutterDataService.clear(path);
                        }
                    }
                }
        );

        Disposer.register(parentDisposable, this);
    }

    /**
     * Updates line status data for all editors based on scope changes.
     * Computes ranges on background threads and publishes results to GutterDataService.
     */
    public void update(Map<String, Change> scopeChangesMap, Map<String, Change> localChangesMap) {
        if (scopeChangesMap == null || disposing.get()) return;

        final DisposalToken token = this.disposalToken;
        final long gen = updateGeneration.incrementAndGet();

        updateExecutor.execute(() -> {
            if (token.disposed || updateGeneration.get() != gen) return;

            Editor[] editors = EditorFactory.getInstance().getAllEditors();

            List<Editor> editorsToUpdate = new ArrayList<>();
            for (Editor editor : editors) {
                if (editor.getEditorKind() == EditorKind.DIFF) continue;
                Document doc = editor.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                if (file == null) continue;
                String filePath = file.getPath();
                if (scopeChangesMap.containsKey(filePath) || publishedFiles.contains(filePath)) {
                    editorsToUpdate.add(editor);
                }
            }

            if (editorsToUpdate.isEmpty()) return;

            Map<String, UpdateInfo> updates = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(editorsToUpdate.size());

            for (Editor editor : editorsToUpdate) {
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        if (!token.disposed) {
                            UpdateInfo info = prepareUpdateForEditor(editor, scopeChangesMap, localChangesMap);
                            if (info != null) {
                                updates.put(info.filePath, info);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!updates.isEmpty() && !token.disposed) {
                List<UpdateInfo> updateList = new ArrayList<>(updates.values());
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!token.disposed) {
                        publishBatchedUpdates(updateList);
                    }
                }, ModalityState.defaultModalityState(), __ -> token.disposed);
            }
        });
    }

    private static class UpdateInfo {
        final String filePath;
        final String baseContent;
        final String headContent;
        final List<Range> precomputedRanges;
        final List<Range> scopeRanges;

        UpdateInfo(String filePath, String baseContent, String headContent,
                   List<Range> ranges, List<Range> scopeRanges) {
            this.filePath = filePath;
            this.baseContent = baseContent;
            this.headContent = headContent;
            this.precomputedRanges = ranges;
            this.scopeRanges = scopeRanges;
        }
    }

    private UpdateInfo prepareUpdateForEditor(Editor editor, Map<String, Change> scopeChangesMap,
                                              Map<String, Change> localChangesMap) {
        if (editor == null || disposing.get()) return null;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return null;

        String filePath = file.getPath();
        Change changeForFile = scopeChangesMap.get(filePath);
        boolean hasLocalChanges = (localChangesMap != null && localChangesMap.containsKey(filePath));

        String baseContent;
        String currentContent;

        if (changeForFile != null && changeForFile.getBeforeRevision() != null) {
            LOG.debug("MyLineStatusTrackerImpl - File: " + filePath + ", beforeRevision: " +
                    (changeForFile.getBeforeRevision() != null ? changeForFile.getBeforeRevision().getRevisionNumber() : "null") +
                    ", afterRevision: " +
                    (changeForFile.getAfterRevision() != null ? changeForFile.getAfterRevision().getRevisionNumber() : "null"));

            try {
                baseContent = changeForFile.getBeforeRevision().getContent();
            } catch (VcsException e) {
                LOG.warn("Error getting content for revision: " + filePath, e);
                baseContent = null;
            }

            if (baseContent == null) {
                baseContent = ApplicationManager.getApplication().runReadAction(
                        (Computable<String>) () -> doc.getCharsSequence().toString());
            }

            currentContent = ApplicationManager.getApplication().runReadAction(
                    (Computable<String>) () -> doc.getImmutableCharSequence().toString());
        } else {
            baseContent = ApplicationManager.getApplication().runReadAction(
                    (Computable<String>) () -> doc.getCharsSequence().toString());
            currentContent = baseContent;
            LOG.debug("MyLineStatusTrackerImpl - File: " + filePath + ", no scope change - clearing markers");
        }

        String normalizedBase = StringUtil.convertLineSeparators(baseContent);
        String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);

        LOG.debug("MyLineStatusTrackerImpl - File: " + filePath +
                ", normalizedBase lines: " + normalizedBase.split("\n").length +
                ", normalizedCurrent lines: " + normalizedCurrent.split("\n").length +
                ", hasLocalChanges: " + hasLocalChanges);

        String headContent = null;
        if (hasLocalChanges) {
            Change localChange = localChangesMap.get(filePath);
            if (localChange != null && localChange.getBeforeRevision() != null) {
                try {
                    headContent = localChange.getBeforeRevision().getContent();
                    if (headContent != null) {
                        headContent = StringUtil.convertLineSeparators(headContent);
                    }
                } catch (VcsException e) {
                    LOG.warn("MyLineStatusTrackerImpl - Error caching HEAD content: " + e.getMessage());
                }
            }
        }

        List<Range> ranges;
        List<Range> scopeRanges = null;
        try {
            if (headContent != null) {
                ranges = computeScopeRangesInCurrentSpace(headContent, normalizedBase, normalizedCurrent, filePath);
                scopeRanges = RangesBuilder.INSTANCE.createRanges(headContent, normalizedBase);
            } else {
                ranges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, normalizedBase);
            }

            LOG.debug("MyLineStatusTrackerImpl - File: " + filePath + ", final ranges: " + ranges.size());
            for (Range range : ranges) {
                LOG.debug("MyLineStatusTrackerImpl - Range: line1=" + range.getLine1() + ", line2=" + range.getLine2() +
                        ", vcsLine1=" + range.getVcsLine1() + ", vcsLine2=" + range.getVcsLine2() + ", type=" + range.getType());
            }
        } catch (Exception e) {
            LOG.error("Error precomputing ranges for: " + filePath, e);
            ranges = Collections.emptyList();
        }

        return new UpdateInfo(filePath, normalizedBase, headContent, ranges, scopeRanges);
    }

    private void publishBatchedUpdates(List<UpdateInfo> updates) {
        for (UpdateInfo update : updates) {
            if (disposing.get()) break;
            GutterDataService.GutterFileData data = new GutterDataService.GutterFileData(
                    update.precomputedRanges, update.baseContent, update.headContent, update.scopeRanges);
            gutterDataService.publish(update.filePath, data);
            publishedFiles.add(update.filePath);
        }
    }

    // --- Range computation (unchanged algorithms) ---

    private List<Range> computeScopeRangesInCurrentSpace(
            String headContent, String normalizedBase, String currentContent, String filePath) {

        List<Range> scopeRanges = RangesBuilder.INSTANCE.createRanges(headContent, normalizedBase);
        LOG.debug("computeScopeRangesInCurrentSpace [" + filePath + "] scope ranges: " + scopeRanges.size());
        if (scopeRanges.isEmpty()) return Collections.emptyList();

        List<Range> localRanges = RangesBuilder.INSTANCE.createRanges(currentContent, headContent);
        LOG.debug("computeScopeRangesInCurrentSpace [" + filePath + "] local ranges: " + localRanges.size());
        if (localRanges.isEmpty()) return new ArrayList<>(scopeRanges);

        return mapScopeRangesToCurrentSpace(scopeRanges, localRanges, filePath);
    }

    private List<Range> mapScopeRangesToCurrentSpace(
            List<Range> scopeRanges, List<Range> localRanges, String filePath) {

        List<Range> result = new ArrayList<>();
        int numLocals = localRanges.size();
        int cumulativeDelta = 0;
        int localIdx = 0;

        for (Range scope : scopeRanges) {
            int headStart = scope.getLine1();
            int headEnd = scope.getLine2();
            int vcsStart = scope.getVcsLine1();
            int vcsEnd = scope.getVcsLine2();

            while (localIdx < numLocals && localRanges.get(localIdx).getVcsLine2() <= headStart) {
                Range local = localRanges.get(localIdx);
                cumulativeDelta += (local.getLine2() - local.getLine1())
                        - (local.getVcsLine2() - local.getVcsLine1());
                localIdx++;
            }

            if (headStart == headEnd) {
                boolean insideLocal = false;
                for (int i = localIdx; i < numLocals; i++) {
                    Range local = localRanges.get(i);
                    if (local.getVcsLine1() > headStart) break;
                    if (local.getVcsLine2() > headStart) { insideLocal = true; break; }
                }
                if (!insideLocal) {
                    int pos = headStart + cumulativeDelta;
                    result.add(new Range(pos, pos, vcsStart, vcsEnd));
                    LOG.debug("mapScopeRangesToCurrentSpace [" + filePath
                            + "] DELETED at current=" + pos + " vcs=[" + vcsStart + "-" + vcsEnd + "]");
                }
                continue;
            }

            int tempLocalIdx = localIdx;
            int headCursor;
            int currentCursor;

            if (tempLocalIdx < numLocals) {
                Range straddle = localRanges.get(tempLocalIdx);
                if (straddle.getVcsLine1() < headStart && straddle.getVcsLine2() > headStart) {
                    headCursor = straddle.getVcsLine2();
                    currentCursor = straddle.getLine2();
                    tempLocalIdx++;
                } else {
                    headCursor = headStart;
                    currentCursor = headStart + cumulativeDelta;
                }
            } else {
                headCursor = headStart;
                currentCursor = headStart + cumulativeDelta;
            }

            while (headCursor < headEnd) {
                Range nextLocal = null;
                if (tempLocalIdx < numLocals) {
                    Range candidate = localRanges.get(tempLocalIdx);
                    if (candidate.getVcsLine1() < headEnd) nextLocal = candidate;
                }

                if (nextLocal != null) {
                    int localHeadStart = nextLocal.getVcsLine1();
                    int localHeadEnd = nextLocal.getVcsLine2();

                    if (localHeadStart > headCursor) {
                        emitScopeSegment(result, headCursor, localHeadStart, currentCursor,
                                headStart, headEnd, vcsStart, vcsEnd, filePath);
                        currentCursor += (localHeadStart - headCursor);
                    }

                    headCursor = Math.max(headCursor, localHeadEnd);
                    currentCursor = nextLocal.getLine2();
                    tempLocalIdx++;
                } else {
                    emitScopeSegment(result, headCursor, headEnd, currentCursor,
                            headStart, headEnd, vcsStart, vcsEnd, filePath);
                    headCursor = headEnd;
                }
            }
        }

        LOG.debug("mapScopeRangesToCurrentSpace [" + filePath
                + "] " + scopeRanges.size() + " scope → " + result.size() + " result ranges");
        return result;
    }

    private void emitScopeSegment(List<Range> result,
                                  int headSegStart, int headSegEnd, int currentStart,
                                  int headBlockStart, int headBlockEnd,
                                  int vcsBlockStart, int vcsBlockEnd, String filePath) {
        int currentEnd = currentStart + (headSegEnd - headSegStart);
        int headBlockLen = headBlockEnd - headBlockStart;
        int segVcsStart, segVcsEnd;

        if (headBlockLen == 0) {
            segVcsStart = vcsBlockStart;
            segVcsEnd = vcsBlockEnd;
        } else {
            long vcsLen = vcsBlockEnd - vcsBlockStart;
            segVcsStart = vcsBlockStart + (int) (vcsLen * (headSegStart - headBlockStart) / headBlockLen);
            segVcsEnd = vcsBlockStart + (int) (vcsLen * (headSegEnd - headBlockStart) / headBlockLen);
        }

        if (currentStart < currentEnd || segVcsStart < segVcsEnd) {
            result.add(new Range(currentStart, currentEnd, segVcsStart, segVcsEnd));
            LOG.debug("emitScopeSegment [" + filePath
                    + "] current=[" + currentStart + "-" + currentEnd
                    + "] vcs=[" + segVcsStart + "-" + segVcsEnd + "]");
        }
    }

    /**
     * Release all published data and disconnect.
     */
    public void releaseAll() {
        if (!disposing.compareAndSet(false, true)) return;

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }

        gutterDataService.clearAll();
        publishedFiles.clear();
    }
}
