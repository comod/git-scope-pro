package implementation.lineStatusTracker;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.actions.GitCompareWithBranchAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.lang.Thread.sleep;

public class MyLineStatusTrackerImpl {

    private final MessageBusConnection messageBusConnection;

    private final Project project;
    private Collection<Change> changes;

    private Map<String, MyLineStatusTrackerManager> myLineStatusTrackerManagerCollection;

    public MyLineStatusTrackerImpl(Project project) {

        this.project = project;

        MessageBus messageBus = this.project.getMessageBus();
        this.messageBusConnection = messageBus.connect();

        // Deactivate the Main Line Status Manager
        showLstGutterMarkers(false);

        // Listen to new opened Tabs
        editorListener();

        init();
    }

    private void init() {

        myLineStatusTrackerManagerCollection = new HashMap<>();
//        this.myLineStatusTrackerManagerCollection = new ArrayList<>();
        // Initialize for open Tabs
        initOpenTabs();

    }


    private void editorListener() {

        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile virtualFile) {

                        Editor editor = getEditorFromVirtualFile(virtualFile);
                        System.out.println("LST FileOpened " + editor);
                        createLineStatus(editor);
                    }
                }
        );

    }

    private Editor getEditorFromVirtualFile(@NotNull VirtualFile virtualFile) {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
            if (editor == null) {
                continue;
            }
            VirtualFile virtualFileFromEditor = getVirtualFileFromEditor(editor);
            if (virtualFileFromEditor == null) {
                continue;
            }
            if (virtualFileFromEditor.equals(virtualFile)) {
                return editor;
            }
        }

        return null;
    }

    public void update(Collection<Change> changes, @Nullable VirtualFile virtualFile) {

        System.out.println("LST update");

        this.changes = changes;
        if (virtualFile != null) {
            // @todo
        }

//        releaseAll();
//
//        // Initialize for open Tabs
//        initOpenTabs();

        updateOpenTabs();

    }

    public void releaseAll() {

        if (this.myLineStatusTrackerManagerCollection == null) {
            return;
        }

        myLineStatusTrackerManagerCollection.forEach((s, myLineStatusTrackerManager) -> {
            myLineStatusTrackerManager.release();
        });

    }

    public void showLstGutterMarkers(Boolean showLstGutterMarkers) {
        // Deactivate/Activate VCS Line Status as it is for now
//        System.out.println("showLstGutterMarkers" + showLstGutterMarkers);
        VcsApplicationSettings vcsApplicationSettings = VcsApplicationSettings.getInstance();
        vcsApplicationSettings.SHOW_LST_GUTTER_MARKERS = showLstGutterMarkers;
    }

    private void initOpenTabs() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        System.out.println("LST editors>");
        for (Editor editor : editors) {
            System.out.println("LST" + editor);
            createLineStatus(editor);
        }
        System.out.println("LST <editors");
    }

    private void updateOpenTabs() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors.length == 0) {
            System.out.println("LST No Editors");
            return;
        }
        for (Editor editor : editors) {
            updateLineStatusByChangesForEditor(editor);
        }
    }

    private void updateLineStatusByChangesForEditor(Editor editor) {

        if (changes == null) {
            System.out.println("LST No Changes");
            return;
        }

        try {
            // Load Revision

            if (editor == null) {
                System.out.println("LST Editor NULL");
                return;
            }

            Document doc = editor.getDocument();
            VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
            if (file == null) {
                return;
            }

            VirtualFile vcsFile;
            boolean isOperationUseful = false;
            ContentRevision contentRevision = null;

            for (Change change : changes) {
                if (change == null) {
                    continue;
                }
                vcsFile = change.getVirtualFile();
                if (vcsFile == null) {
                    continue;
                }

                if (vcsFile.getPath().equals(file.getPath())) {
                    contentRevision = change.getBeforeRevision();
                    isOperationUseful = true;
                    break;
                }
            }

            String content = "";

            if (!isOperationUseful) {
                content = doc.getCharsSequence().toString();
            }

            if (contentRevision != null) {
                content = contentRevision.getContent();

                if (content == null) {
                    return;
                }
            }

            System.out.println("LST setContent: " + editor + content);
            setContent(editor, content);

        } catch (VcsException e) {
            System.out.println("XXX (LineStatus) Exc: " + e.getMessage());
        }
    }


    private void setContent(Editor editor, String content) {

        content = StringUtil.convertLineSeparators(content);
        MyLineStatusTrackerManager myLineStatusTrackerManager = myLineStatusTrackerManagerCollection.get(getPathFromEditor(editor));

//        System.out.println("get " + getPathFromEditor(editor));
        if (myLineStatusTrackerManager == null) {
//            System.out.println("null");
            return;
        }

        myLineStatusTrackerManager.setBaseRevision(content);

    }

    private String getPathFromEditor(Editor editor) {

        VirtualFile virtualFile = getVirtualFileFromEditor(editor);
        if (virtualFile == null) {
            return null;
        }

        return virtualFile.getPath();
    }

    private VirtualFile getVirtualFileFromEditor(Editor editor) {
        Document document = editor.getDocument();
        return FileDocumentManager.getInstance().getFile(document);
    }

    private void createLineStatus(@Nullable Editor editor) {

        if (editor == null) {
            return;
        }

        MyLineStatusTrackerManager myLineStatusTrackerManagerCache = myLineStatusTrackerManagerCollection.get(getPathFromEditor(editor));
        if (myLineStatusTrackerManagerCache != null) {
//            System.out.println("cache");
            return;
        }

        Document document = editor.getDocument();
        MyLineStatusTrackerManager myLineStatusTrackerManager = new MyLineStatusTrackerManager(
                project,
                document
        );

//        System.out.println("put " + getPathFromEditor(editor));
        myLineStatusTrackerManagerCollection.put(getPathFromEditor(editor), myLineStatusTrackerManager);

    }

}