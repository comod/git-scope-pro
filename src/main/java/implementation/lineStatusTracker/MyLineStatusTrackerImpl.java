package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

public class MyLineStatusTrackerImpl implements Disposable {
    private static final Logger LOG = Logger.getInstance(MyLineStatusTrackerImpl.class);
    private final MessageBusConnection messageBusConnection;
    private final Map<String, TrackerInfo> trackerInfoMap = new HashMap<>();
    private final LineStatusTrackerManagerI trackerManager;

    // Store both the requester and the base content for each file
    private static class TrackerInfo {
        Object requester;
        String baseContent;

        TrackerInfo(Object requester, String baseContent) {
            this.requester = requester;
            this.baseContent = baseContent;
        }
    }

    @Override
    public void dispose() {
        releaseAll();
    }

    public MyLineStatusTrackerImpl(Project project, Disposable parentDisposable) {
        this.trackerManager = project.getService(LineStatusTrackerManagerI.class);

        // Subscribe to file editor events
        MessageBus messageBus = project.getMessageBus();
        this.messageBusConnection = messageBus.connect();

        // Listen to file open events
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
                }
        );

        Disposer.register(parentDisposable, this);
    }

    private boolean isDiffView(Editor editor) {
        return editor.getEditorKind() == EditorKind.DIFF;
    }

    private void refreshEditor(Editor editor) {
        editor.getMarkupModel().removeAllHighlighters();
        if (editor.getGutter() instanceof EditorGutterComponentEx gutter) {
            gutter.revalidateMarkup();
            gutter.repaint();
        }
        editor.getComponent().repaint();
    }

    public void update(Collection<Change> changes, @Nullable VirtualFile targetFile) {
        if (changes == null) {
            return;
        }

        // Move VFS operations to background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            // Collect all necessary data from VFS in background thread with read action
            Map<String, ContentRevision> fileToRevisionMap = collectFileRevisionMap(changes);

            // Switch to EDT only for UI updates
            ApplicationManager.getApplication().invokeLater(() -> {
                Editor[] editors = EditorFactory.getInstance().getAllEditors();
                for (Editor editor : editors) {
                    if (isDiffView(editor)) {
                        continue;
                    }
                    updateLineStatusByChangesForEditorSafe(editor, fileToRevisionMap);
                    refreshEditor(editor);
                }
            });
        });
    }

    private Map<String, ContentRevision> collectFileRevisionMap(Collection<Change> changes) {
        return ApplicationManager.getApplication().runReadAction((Computable<Map<String, ContentRevision>>) () -> {
            Map<String, ContentRevision> map = new HashMap<>();
            for (Change change : changes) {
                if (change == null) continue;

                VirtualFile vcsFile = change.getVirtualFile(); // Safe on background thread
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

    private void updateLineStatusByChangesForEditorSafe(Editor editor, Map<String, ContentRevision> fileToRevisionMap) {
        if (editor == null) return;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return;

        String filePath = file.getPath();
        ContentRevision contentRevision = fileToRevisionMap.get(filePath);

        String content = "";
        if (contentRevision == null) {
            content = doc.getCharsSequence().toString();
        } else {
            try {
                String revisionContent = contentRevision.getContent();
                if (revisionContent != null) {
                    content = revisionContent;
                }
            } catch (VcsException e) {
                LOG.warn("Error getting content for revision", e);
                return;
            }
        }

        // Update the tracker with the content
        updateTrackerBaseContent(doc, content);
    }

    /**
     * Update the base content of the line status tracker using the setBaseRevision method
     */
    private void updateTrackerBaseContent(Document document, String content) {
        if (content == null) return;

        content = StringUtil.convertLineSeparators(content);
        final String finalContent = content;

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                if (file == null) return;

                String filePath = file.getPath();

                // Update our cache
                TrackerInfo trackerInfo = trackerInfoMap.get(filePath);
                if (trackerInfo != null) {
                    trackerInfo.baseContent = finalContent;
                } else {
                    // Create a new requester if we don't have one
                    Object requester = new Object();
                    trackerInfo = new TrackerInfo(requester, finalContent);
                    trackerInfoMap.put(filePath, trackerInfo);

                    // Request a tracker for this document if we don't have one
                    trackerManager.requestTrackerFor(document, requester);
                }

                // Get the actual LineStatusTracker instance and update its base content
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
     * Use reflection to call the setBaseRevision method on the tracker
     */
    private void updateTrackerBaseRevision(LineStatusTracker<?> tracker, String content) {
        try {
            // Find the setBaseRevision method in the tracker class hierarchy
            Method setBaseRevisionMethod = findMethodInHierarchy(tracker.getClass(), "setBaseRevision", CharSequence.class);

            if (setBaseRevisionMethod != null) {
                setBaseRevisionMethod.setAccessible(true);
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
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
     * Helper method to find a method in the class hierarchy
     */
    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                // Try superclass
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private synchronized void requestLineStatusTracker(@Nullable Editor editor) {
        if (editor == null) return;

        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file == null) return;

        String filePath = file.getPath();
        if (trackerInfoMap.containsKey(filePath)) {
            return;
        }

        Document document = editor.getDocument();

        // Request a tracker for this document
        try {
            Object requester = new Object(); // Unique requester object

            // Add to our map with empty base content for now
            trackerInfoMap.put(filePath, new TrackerInfo(requester, ""));

            // Request a tracker for this document
            trackerManager.requestTrackerFor(document, requester);

            // Force refresh of editor gutter after requesting tracker
            ApplicationManager.getApplication().invokeLater(() -> {
                if (editor.getGutter() instanceof EditorGutterComponentEx gutter) {
                    gutter.revalidateMarkup();
                }
            });
        } catch (Exception e) {
            LOG.error("Failed to request line status tracker", e);
        }
    }

    public synchronized void releaseAll() {
        for (Map.Entry<String, TrackerInfo> entry : trackerInfoMap.entrySet()) {
            try {
                String filePath = entry.getKey();
                TrackerInfo trackerInfo = entry.getValue();
                Object requester = trackerInfo.requester;

                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (file != null) {
                    Document document = FileDocumentManager.getInstance().getDocument(file);
                    if (document != null) {
                        // Log before releasing for debugging
                        trackerManager.releaseTrackerFor(document, requester);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error releasing tracker", e);
            }
        }

        trackerInfoMap.clear();
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
    }
}