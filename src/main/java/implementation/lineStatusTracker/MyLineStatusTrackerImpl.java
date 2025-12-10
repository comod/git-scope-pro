package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.util.DocumentUtil;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
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
    private final MessageBusConnection messageBusConnection;
    private final LineStatusTrackerManagerI trackerManager;
    private final CommitDiffWorkaround commitDiffWorkaround;

    // Single, consistent requester for this component's lifetime
    private final Object requester = new Object();
    private final AtomicBoolean disposing = new AtomicBoolean(false);

    // Track per-document holds and base content
    private final Map<Document, TrackerInfo> trackers = new HashMap<>();

    private static final class TrackerInfo {
        volatile boolean held;
        volatile String customBaseContent;  // Custom base (target branch)
        volatile String headBaseContent;     // HEAD revision (for commit panel diffs)
        volatile boolean isShowingHeadBase;  // Currently showing HEAD base (vs custom base)

        TrackerInfo(boolean held, String baseContent) {
            this.held = held;
            this.customBaseContent = baseContent;
            this.headBaseContent = null;
            this.isShowingHeadBase = false;
        }
    }

    @Override
    public void dispose() {
        releaseAll();
    }

    public MyLineStatusTrackerImpl(Project project, Disposable parentDisposable) {
        this.project = project;
        this.trackerManager = project.getService(LineStatusTrackerManagerI.class);

        // Initialize the commit diff workaround with our base revision switcher implementation
        this.commitDiffWorkaround = new CommitDiffWorkaround(project, new CommitDiffWorkaround.BaseRevisionSwitcher() {
            @Override
            public void switchToHeadBase(@NotNull Document document, @NotNull String headContent) {
                LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
                if (tracker != null) {
                    updateTrackerBaseRevision(tracker, headContent);
                }
            }

            @Override
            public void switchToCustomBase(@NotNull Document document, @NotNull String customContent) {
                LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
                if (tracker != null) {
                    updateTrackerBaseRevision(tracker, customContent);
                }
            }

            @Override
            public @Nullable String getCachedHeadContent(@NotNull Document document) {
                TrackerInfo info = trackers.get(document);
                return info != null ? info.headBaseContent : null;
            }

            @Override
            public @Nullable String getCachedCustomBaseContent(@NotNull Document document) {
                TrackerInfo info = trackers.get(document);
                return info != null ? info.customBaseContent : null;
            }

            @Override
            public void cacheHeadContent(@NotNull Document document, @NotNull String headContent) {
                TrackerInfo info = trackers.get(document);
                if (info != null) {
                    info.headBaseContent = headContent;
                }
            }

            @Override
            public boolean isTracked(@NotNull Document document) {
                TrackerInfo info = trackers.get(document);
                return info != null && info.held;
            }

            @Override
            public void markShowingHeadBase(@NotNull Document document, boolean showing) {
                TrackerInfo info = trackers.get(document);
                if (info != null) {
                    info.isShowingHeadBase = showing;
                }
            }

            @Override
            public boolean isShowingHeadBase(@NotNull Document document) {
                TrackerInfo info = trackers.get(document);
                return info != null && info.isShowingHeadBase;
            }
        });

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
                            // Workaround: Don't release if commit diff editors still need this document
                            if (!commitDiffWorkaround.hasCommitDiffEditorsFor(doc)) {
                                safeRelease(doc);
                            }
                        }

                        // Workaround: Check if closing this file returns focus to commit diff
                        FileEditor selectedEditor = source.getSelectedEditor();
                        if (selectedEditor != null &&
                            selectedEditor.getClass().getSimpleName().equals("BackendDiffRequestProcessorEditor")) {
                            commitDiffWorkaround.handleSwitchedToCommitDiff();
                        }
                    }

                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        handleEditorSelectionChanged(event);
                    }
                }
        );

        // Listen to editor lifecycle to detect commit panel diff editors
        EditorFactory.getInstance().addEditorFactoryListener(
                new EditorFactoryListener() {
                    @Override
                    public void editorCreated(@NotNull EditorFactoryEvent event) {
                        Editor editor = event.getEditor();
                        if (editor.getEditorKind() == EditorKind.DIFF) {
                            // Check if it's a commit panel diff - hierarchy might not be ready yet
                            ApplicationManager.getApplication().invokeLater(() -> {
                                if (commitDiffWorkaround.isCommitPanelDiff(editor)) {
                                    commitDiffWorkaround.handleCommitDiffEditorCreated(editor);
                                }
                            });
                        }
                    }

                    @Override
                    public void editorReleased(@NotNull EditorFactoryEvent event) {
                        Editor editor = event.getEditor();
                        if (commitDiffWorkaround.isCommitPanelDiff(editor)) {
                            commitDiffWorkaround.handleCommitDiffEditorReleased(editor);
                        }
                    }
                },
                parentDisposable
        );

        Disposer.register(parentDisposable, this);
        Disposer.register(parentDisposable, commitDiffWorkaround);
    }

    private boolean isDiffView(Editor editor) {
        return editor.getEditorKind() == EditorKind.DIFF;
    }

    public void update(Collection<Change> changes, @Nullable VirtualFile targetFile) {
        if (changes == null || disposing.get()) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (disposing.get()) return;

            Map<String, ContentRevision> fileToRevisionMap = collectFileRevisionMap(changes);

            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposing.get()) return;

                Editor[] editors = EditorFactory.getInstance().getAllEditors();
                for (Editor editor : editors) {
                    if (isDiffView(editor)) continue;
                    // Platform handles gutter repainting automatically - no need to force it
                    updateLineStatusByChangesForEditorSafe(editor, fileToRevisionMap);
                }
            });
        });
    }

    private Map<String, ContentRevision> collectFileRevisionMap(Collection<Change> changes) {
        return ApplicationManager.getApplication().runReadAction((Computable<Map<String, ContentRevision>>) () -> {
            Map<String, ContentRevision> map = new HashMap<>();
            for (Change change : changes) {
                if (change == null) continue;

                VirtualFile vcsFile = change.getVirtualFile(); // background thread
                if (vcsFile == null) continue;

                String filePath = vcsFile.getPath();
                ContentRevision beforeRevision = change.getBeforeRevision();
                if (beforeRevision != null) {
                    map.put(filePath, beforeRevision);
                }
            }
            return map;
        });
    }

    private boolean updateLineStatusByChangesForEditorSafe(Editor editor, Map<String, ContentRevision> fileToRevisionMap) {
        if (editor == null || disposing.get()) return false;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return false;

        String filePath = file.getPath();
        ContentRevision contentRevision = fileToRevisionMap.get(filePath);

        String content;
        if (contentRevision == null) {
            content = doc.getCharsSequence().toString();
        } else {
            try {
                String revisionContent = contentRevision.getContent();
                content = revisionContent != null ? revisionContent : "";
            } catch (VcsException e) {
                LOG.warn("Error getting content for revision", e);
                return false;
            }
        }

        updateTrackerBaseContent(doc, content);
        return true;
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

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposing.get()) return;

            try {
                ensureRequested(document);

                TrackerInfo info = trackers.get(document);
                if (info != null) {
                    info.customBaseContent = finalContent;

                    // Workaround: Skip custom base update if commit diff is active (showing HEAD base)
                    if (commitDiffWorkaround.shouldSkipCustomBaseUpdate(document)) {
                        return;
                    }
                }

                LineStatusTracker<?> tracker = trackerManager.getLineStatusTracker(document);
                if (tracker != null) {
                    updateTrackerBaseRevision(tracker, finalContent);
                }
            } catch (Exception e) {
                LOG.error("Error updating line status tracker with new base content", e);
            }
        });
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
                        
                        // Use bulk update mode to batch changes and prevent flickering
                        Document document = tracker.getDocument();
                        if (document == null) {
                            return;
                        }
                        
                        DocumentUtil.executeInBulk(document, () -> {
                            try {
                                setBaseRevisionMethod.invoke(tracker, content);
                            } catch (Exception e) {
                                LOG.error("Failed to invoke setBaseRevision method", e);
                            }
                        });
                    } catch (Exception e) {
                        LOG.error("Failed to execute in bulk mode", e);
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
    private synchronized void safeRelease(@NotNull Document document) {
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
    }

    /**
     * Handle editor selection changed - detect switching to/from commit diff tabs.
     * Delegates to CommitDiffWorkaround for handling commit panel diff base revision switching.
     */
    private void handleEditorSelectionChanged(@NotNull FileEditorManagerEvent event) {
        FileEditor oldEditor = event.getOldEditor();
        FileEditor newEditor = event.getNewEditor();

        // Workaround: Check if switching away from commit diff editor
        if (oldEditor != null && oldEditor.getClass().getSimpleName().equals("BackendDiffRequestProcessorEditor")) {
            commitDiffWorkaround.handleSwitchedAwayFromCommitDiff();
        }

        // Workaround: Check if switching to commit diff editor
        if (newEditor != null && newEditor.getClass().getSimpleName().equals("BackendDiffRequestProcessorEditor")) {
            commitDiffWorkaround.handleSwitchedToCommitDiff();
        }
    }
}
