package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyLineStatusTrackerImpl implements Disposable {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(MyLineStatusTrackerImpl.class);

    private final Project project;
    private MessageBusConnection messageBusConnection;
    private LineStatusTrackerManagerI trackerManager;

    // Single, consistent requester for this component's lifetime
    private final Object requester = new Object();
    private final AtomicBoolean disposing = new AtomicBoolean(false);

    // Lightweight disposable token to check disposal state without capturing 'this'
    private static class DisposalToken {
        volatile boolean disposed = false;
    }
    private final DisposalToken disposalToken = new DisposalToken();

    // Track per-document holds and base content
    private final Map<Document, TrackerInfo> trackers = new HashMap<>();

    private static final class TrackerInfo {
        volatile boolean held;
        volatile String customBaseContent;  // Custom base (target branch)

        TrackerInfo(boolean held, String baseContent) {
            this.held = held;
            this.customBaseContent = baseContent;
        }
    }

    @Override
    public void dispose() {
        disposalToken.disposed = true;
        releaseAll();
    }

    public MyLineStatusTrackerImpl(Project project, Disposable parentDisposable) {
        this.project = project;
        this.trackerManager = project.getService(LineStatusTrackerManagerI.class);

        MessageBus messageBus = project.getMessageBus();
        this.messageBusConnection = messageBus.connect();

        // Listen to file open/close/selection events
        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile virtualFile) {
                        Editor editor = fileEditorManager.getSelectedTextEditor();
                        if (editor != null) {
                            requestLineStatusTracker(editor);
                        }
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

    public void update(Map<String, Change> scopeChangesMap) {
        if (scopeChangesMap == null || disposing.get()) {
            return;
        }

        final DisposalToken token = this.disposalToken;

        // Process editors on background thread to avoid blocking EDT with file system operations
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (token.disposed) return;

            Editor[] editors = EditorFactory.getInstance().getAllEditors();
            for (Editor editor : editors) {
                if (isDiffView(editor)) continue;
                // Platform handles gutter repainting automatically - no need to force it
                updateLineStatusByChangesForEditor(editor, scopeChangesMap, token);
            }
        });
    }

    private void updateLineStatusByChangesForEditor(Editor editor, Map<String, Change> scopeChangesMap, DisposalToken token) {
        if (editor == null || disposing.get()) return;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return;

        String filePath = file.getPath();
        Change changeForFile = scopeChangesMap.get(filePath);

        String content;
        if (changeForFile != null && changeForFile.getBeforeRevision() != null) {
            // Extract content for this specific file only
            try {
                content = changeForFile.getBeforeRevision().getContent();
            } catch (VcsException e) {
                LOG.warn("Error getting content for revision: " + filePath, e);
                content = null;
            }

            if (content == null) {
                content = com.intellij.openapi.application.ReadAction.compute(() -> doc.getCharsSequence().toString());
            }
        } else {
            // No revision content available, use current document content
            content = com.intellij.openapi.application.ReadAction.compute(() -> doc.getCharsSequence().toString());
        }

        // Update tracker on EDT
        String finalContent = content;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!token.disposed) {
                updateTrackerBaseContent(doc, finalContent);
            }
        }, ModalityState.defaultModalityState(), __ -> token.disposed);
    }

    /**
     * Ensure a tracker is requested once for the given document.
     */
    private synchronized void ensureRequested(@NotNull Document document) {
        if (disposing.get()) return;

        TrackerInfo info = trackers.computeIfAbsent(document, k -> new TrackerInfo(false, ""));

        if (!info.held) {
            try {
                trackerManager.requestTrackerFor(document, requester);
                info.held = true;
            } catch (Exception e) {
                LOG.error("Failed to request line status tracker", e);
            }
        }
    }

    /**
     * Update the base content of the line status tracker for the document.
     */
    private void updateTrackerBaseContent(Document document, String content) {
        if (content == null || disposing.get()) return;

        final String finalContent = StringUtil.convertLineSeparators(content);
        final DisposalToken token = this.disposalToken;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (token.disposed) return;

            try {
                ensureRequested(document);

                TrackerInfo info = trackers.get(document);
                if (info != null) {
                    info.customBaseContent = finalContent;
                }

                LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
                if (tracker != null) {
                    updateTrackerBaseRevision(tracker, finalContent);
                }
            } catch (Exception e) {
                LOG.error("Error updating line status tracker with new base content", e);
            }
        }, ModalityState.defaultModalityState(), __ -> token.disposed);
    }

    /**
     * Request a tracker for the editor's document (no duplicate requester).
     */
    private void requestLineStatusTracker(@Nullable Editor editor) {
        if (editor == null || disposing.get()) return;

        Document document = editor.getDocument();
        ensureRequested(document);

        // Platform handles gutter repainting automatically - no need to force it
    }

    /**
     * Use reflection to call the setBaseRevision method on the tracker.
     * Uses bulk update mode to batch document changes and reduce daemon restarts.
     */
    private void updateTrackerBaseRevision(LineStatusTracker<?> tracker, String content) {
        try {

            // TODO: Reflection used to get access to setBaseRevision method
            // Find the setBaseRevision method in the tracker class hierarchy
            Method setBaseRevisionMethod = findMethodInHierarchy(tracker.getClass(), "setBaseRevision", CharSequence.class);

            if (setBaseRevisionMethod != null) {
                setBaseRevisionMethod.setAccessible(true);
                // Guard against concurrent disposal
                if (disposing.get()) {
                    return;
                }
                
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        // Double-check disposal state inside write action
                        if (disposing.get()) {
                            return;
                        }

                        setBaseRevisionMethod.invoke(tracker, content);
                    } catch (Exception e) {
                        LOG.error("Failed to invoke setBaseRevision method", e);
                    }
                });
            } else {
                LOG.warn("setBaseRevision method not found in tracker class: " + tracker.getClass().getName());
            }

        } catch (Exception e) {
            LOG.error("Error accessing setBaseRevision method via reflection", e);
        }
    }

    /**
     * Helper to find a method in the class hierarchy.
     */
    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Release for a specific document if we hold it.
     */
    private synchronized void release(@NotNull Document document) {
        TrackerInfo info = trackers.get(document);
        if (info == null || !info.held) return;

        try {
            LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
            if (tracker != null) {
                trackerManager.releaseTrackerFor(document, requester);
            }
            info.held = false;
        } catch (Exception e) {
            LOG.warn("Error releasing tracker for document", e);
        }
    }

    /**
     * Release all trackers we hold. Prevents new requests, and avoids underflow by
     * releasing only entries still marked as held and having a tracker instance.
     */
    public void releaseAll() {
        if (!disposing.compareAndSet(false, true)) {
            return; // already disposing
        }

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }

        Runnable release = () -> {
            for (Map.Entry<Document, TrackerInfo> entry : trackers.entrySet()) {
                Document document = entry.getKey();
                TrackerInfo info = entry.getValue();
                if (info == null || !info.held) continue;

                try {
                    LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
                    if (tracker != null) {
                        trackerManager.releaseTrackerFor(document, requester);
                    }
                    info.held = false;
                } catch (Exception e) {
                    LOG.warn("Error releasing tracker", e);
                }
            }
            trackers.clear();
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            release.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(release);
        }

        // Null out references to platform services to prevent retention
        trackerManager = null;
        messageBusConnection = null;
    }

}
