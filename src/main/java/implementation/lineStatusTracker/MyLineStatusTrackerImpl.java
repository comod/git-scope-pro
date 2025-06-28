package implementation.lineStatusTracker;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MyLineStatusTrackerImpl {
    private static final Logger LOG = Logger.getInstance(MyLineStatusTrackerImpl.class);
    private final Project project;
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

    public MyLineStatusTrackerImpl(Project project) {
        this.project = project;
        this.trackerManager = project.getService(LineStatusTrackerManagerI.class);

        // Subscribe to file editor events
        MessageBus messageBus = this.project.getMessageBus();
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
    }

    public void update(Collection<Change> changes, @Nullable VirtualFile targetFile) {
        if (changes == null) {
            return;
        }

        // Update all open editors with the changes
        ApplicationManager.getApplication().invokeLater(() -> {
            Editor[] editors = EditorFactory.getInstance().getAllEditors();
            for (Editor editor : editors) {
                updateLineStatusByChangesForEditor(editor, changes);
            }
        });
    }

    private void updateLineStatusByChangesForEditor(Editor editor, Collection<Change> changes) {
        if (editor == null) return;

        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return;

        String filePath = file.getPath();

        // Check for specific changes for this file
        boolean isOperationUseful = false;
        ContentRevision contentRevision = null;

        // Look for matching change for this file
        for (Change change : changes) {
            if (change == null) continue;

            VirtualFile vcsFile = change.getVirtualFile();
            if (vcsFile == null) continue;

            if (vcsFile.getPath().equals(filePath)) {
                contentRevision = change.getBeforeRevision();
                isOperationUseful = true;
                break;
            }
        }

        // If no match was found and no useful operation, use current content
        String content = "";
        if (!isOperationUseful) {
            content = doc.getCharsSequence().toString();
        }

        // If we found a content revision, use it
        if (contentRevision != null) {
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

                    // Force refresh of all editors for this document
                    refreshEditorsForDocument(document);
                } else {
                    LOG.debug("No LineStatusTracker found for document: " + filePath);
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

    /**
     * Force refresh all editors displaying this document
     */
    private void refreshEditorsForDocument(Document document) {
        for (Editor editor : EditorFactory.getInstance().getEditors(document)) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                editor.getMarkupModel().removeAllHighlighters();
            });

            if (editor.getGutter() instanceof EditorGutterComponentEx gutter) {
                gutter.revalidateMarkup();
                gutter.repaint();
            }

            editor.getComponent().repaint();
        }
    }

    private void requestLineStatusTracker(@Nullable Editor editor) {
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
                if (editor.getGutter() instanceof EditorGutterComponentEx) {
                    EditorGutterComponentEx gutter = (EditorGutterComponentEx) editor.getGutter();
                    gutter.revalidateMarkup();
                }
            });
        } catch (Exception e) {
            LOG.error("Failed to request line status tracker", e);
        }
    }

    /**
     * Clean up all trackers when the plugin is unloaded or project is closed
     */
    public void releaseAll() {
        for (Map.Entry<String, TrackerInfo> entry : trackerInfoMap.entrySet()) {
            try {
                String filePath = entry.getKey();
                TrackerInfo trackerInfo = entry.getValue();

                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (file != null) {
                    Document document = FileDocumentManager.getInstance().getDocument(file);
                    if (document != null) {
                        trackerManager.releaseTrackerFor(document, trackerInfo.requester);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error releasing tracker", e);
            }
        }

        trackerInfoMap.clear();
        messageBusConnection.disconnect();
    }
}