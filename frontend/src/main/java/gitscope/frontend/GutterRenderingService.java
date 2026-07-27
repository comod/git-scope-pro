package gitscope.frontend;

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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import implementation.gutter.Range;
import implementation.gutter.RangesBuilder;
import implementation.gutter.ScopeLineStatusMarkerRenderer;
import org.jetbrains.annotations.NotNull;
import service.GutterDataService;
import system.Defs;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Frontend service that manages gutter renderers based on data published by the backend
 * via {@link GutterDataService}.
 * <p>
 * Handles renderer lifecycle, document change listeners for live recomputation,
 * and file close cleanup.
 */
public class GutterRenderingService implements Disposable, GutterDataService.Listener {
    private static final Logger LOG = Defs.getLogger(GutterRenderingService.class);

    private final Project project;
    private final GutterDataService gutterDataService;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Map<Document, RendererInfo> renderers = new HashMap<>();
    private MessageBusConnection messageBusConnection;

    private static final class RendererInfo {
        volatile ScopeLineStatusMarkerRenderer renderer;
        volatile String baseContent;
        volatile String headContent;
        volatile List<Range> scopeRanges;
        volatile DocumentListener documentListener;

        RendererInfo(ScopeLineStatusMarkerRenderer renderer, String baseContent) {
            this.renderer = renderer;
            this.baseContent = baseContent;
        }
    }

    public GutterRenderingService(Project project) {
        this.project = project;
        this.gutterDataService = project.getService(GutterDataService.class);
        LOG.info("GutterRenderingService created, registering as listener on GutterDataService");
        this.gutterDataService.addListener(this);

        this.messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        // Check if backend already has data for this file (startup race fix)
                        GutterDataService.GutterFileData data = gutterDataService.getData(file.getPath());
                        if (data != null) {
                            onDataUpdated(file.getPath(), data);
                        }
                    }

                    @Override
                    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        Document doc = ApplicationManager.getApplication().runReadAction(
                                (Computable<Document>) () -> FileDocumentManager.getInstance().getDocument(file));
                        if (doc != null) {
                            releaseRenderer(doc);
                        }
                    }
                }
        );

        // Replay existing data for all currently open editors (handles startup race)
        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed.get()) return;
            for (Map.Entry<String, GutterDataService.GutterFileData> entry : gutterDataService.getAllData().entrySet()) {
                onDataUpdated(entry.getKey(), entry.getValue());
            }
        }, ModalityState.any());
    }

    // --- GutterDataService.Listener ---

    @Override
    public void onDataUpdated(@NotNull String filePath, @NotNull GutterDataService.GutterFileData data) {
        if (disposed.get()) return;
        LOG.info("GutterRenderingService.onDataUpdated: file=" + filePath + ", ranges=" + data.ranges.size());

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed.get()) return;

            // Find the document for this file path
            boolean found = false;
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (editor.getEditorKind() == EditorKind.DIFF) continue;
                Document doc = editor.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                if (file != null && file.getPath().equals(filePath)) {
                    LOG.info("GutterRenderingService: found editor for " + filePath + ", updating renderer");
                    updateRenderer(doc, file, data);
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOG.info("GutterRenderingService: NO editor found for " + filePath);
            }
        }, ModalityState.defaultModalityState(), __ -> disposed.get());
    }

    @Override
    public void onDataCleared(@NotNull String filePath) {
        if (disposed.get()) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed.get()) return;
            for (Map.Entry<Document, RendererInfo> entry : new ArrayList<>(renderers.entrySet())) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(entry.getKey());
                if (file != null && file.getPath().equals(filePath)) {
                    releaseRenderer(entry.getKey());
                    break;
                }
            }
        }, ModalityState.defaultModalityState(), __ -> disposed.get());
    }

    @Override
    public void onAllCleared() {
        if (disposed.get()) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed.get()) return;
            releaseAllRenderers();
        }, ModalityState.defaultModalityState(), __ -> disposed.get());
    }

    // --- Renderer management ---

    private synchronized void updateRenderer(@NotNull Document document, @NotNull VirtualFile file,
                                             @NotNull GutterDataService.GutterFileData data) {
        if (disposed.get()) return;

        RendererInfo info = renderers.get(document);
        if (info == null) {
            LOG.info("GutterRenderingService.updateRenderer: CREATING new renderer for " + file.getPath());
            ScopeLineStatusMarkerRenderer renderer = new ScopeLineStatusMarkerRenderer(
                    project, document, file, this);
            info = new RendererInfo(renderer, data.baseContent);
            renderers.put(document, info);

            DocumentListener docListener = createDocumentListener(document, info);
            info.documentListener = docListener;
            document.addDocumentListener(docListener, this);
        } else {
            info.baseContent = data.baseContent;
        }

        info.headContent = data.headContent;
        info.scopeRanges = data.scopeRanges;
        info.renderer.setVcsBaseContent(data.baseContent);
        info.renderer.updateRanges(data.ranges);
        LOG.info("GutterRenderingService.updateRenderer: applied " + data.ranges.size() + " ranges to " + file.getPath());
    }

    private DocumentListener createDocumentListener(@NotNull Document document, @NotNull RendererInfo info) {
        return new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (disposed.get() || info.baseContent == null) return;

                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    if (!disposed.get()) {
                        recalculateRangesAsync(document, info);
                    }
                });
            }
        };
    }

    private void recalculateRangesAsync(@NotNull Document document, @NotNull RendererInfo info) {
        try {
            if (!VcsApplicationSettings.getInstance().SHOW_LST_GUTTER_MARKERS) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed.get() && info.renderer != null) {
                        info.renderer.updateRanges(Collections.emptyList());
                    }
                }, ModalityState.defaultModalityState());
                return;
            }

            String currentContent = ApplicationManager.getApplication().runReadAction(
                    (Computable<String>) () -> document.getImmutableCharSequence().toString());
            String normalizedCurrent = StringUtil.convertLineSeparators(currentContent);

            List<Range> filteredRanges;
            if (info.headContent != null && info.scopeRanges != null) {
                List<Range> localRanges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, info.headContent);
                if (localRanges.isEmpty()) {
                    filteredRanges = new ArrayList<>(info.scopeRanges);
                } else {
                    filteredRanges = mapScopeRangesToCurrentSpace(info.scopeRanges, localRanges);
                }
            } else {
                filteredRanges = RangesBuilder.INSTANCE.createRanges(normalizedCurrent, info.baseContent);
            }

            final List<Range> rangesToApply = filteredRanges;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!disposed.get() && info.renderer != null) {
                    info.renderer.updateRanges(rangesToApply);
                }
            }, ModalityState.defaultModalityState());
        } catch (Exception e) {
            LOG.error("Error recalculating ranges", e);
        }
    }

    /**
     * Maps scope ranges from HEAD coordinate space into current-document space,
     * splitting or suppressing portions that overlap with local changes.
     * Same algorithm as MyLineStatusTrackerImpl.mapScopeRangesToCurrentSpace.
     */
    private List<Range> mapScopeRangesToCurrentSpace(List<Range> scopeRanges, List<Range> localRanges) {
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
                                headStart, headEnd, vcsStart, vcsEnd);
                        currentCursor += (localHeadStart - headCursor);
                    }

                    headCursor = Math.max(headCursor, localHeadEnd);
                    currentCursor = nextLocal.getLine2();
                    tempLocalIdx++;
                } else {
                    emitScopeSegment(result, headCursor, headEnd, currentCursor,
                            headStart, headEnd, vcsStart, vcsEnd);
                    headCursor = headEnd;
                }
            }
        }
        return result;
    }

    private void emitScopeSegment(List<Range> result,
                                  int headSegStart, int headSegEnd, int currentStart,
                                  int headBlockStart, int headBlockEnd,
                                  int vcsBlockStart, int vcsBlockEnd) {
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
        }
    }

    private synchronized void releaseRenderer(@NotNull Document document) {
        RendererInfo info = renderers.remove(document);
        if (info != null) {
            if (info.documentListener != null) {
                try { document.removeDocumentListener(info.documentListener); }
                catch (Exception e) { LOG.warn("Error removing document listener", e); }
            }
            if (info.renderer != null) {
                try { info.renderer.dispose(); }
                catch (Exception e) { LOG.warn("Error disposing renderer", e); }
            }
        }
    }

    private void releaseAllRenderers() {
        for (Map.Entry<Document, RendererInfo> entry : renderers.entrySet()) {
            RendererInfo info = entry.getValue();
            if (info != null && info.renderer != null) {
                try { info.renderer.dispose(); }
                catch (Exception e) { LOG.warn("Error disposing renderer", e); }
            }
        }
        renderers.clear();
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;

        gutterDataService.removeListener(this);

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }

        Runnable release = this::releaseAllRenderers;
        if (ApplicationManager.getApplication().isDispatchThread()) {
            release.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(release);
        }
    }
}
