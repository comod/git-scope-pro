package toolwindow.elements;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;
import toolwindow.VcsTreeActions;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

public class MySimpleChangesBrowser extends SimpleAsyncChangesBrowser {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(MySimpleChangesBrowser.class);
    private final Project myProject;
    UISettings uiSettings = UISettings.getInstance();

    // Instance-level actions to avoid static references that prevent plugin unloading
    // Use lazy initialization since super() constructor may call createToolbarActions() before field initialization
    private AnAction selectOpenedFileAction;
    private AnAction showInProjectAction;
    private AnAction rollbackAction;
    private List<AnAction> toolbarActions;

    private void initializeActions() {
        if (selectOpenedFileAction == null) {
            selectOpenedFileAction = new VcsTreeActions.SelectOpenedFileAction();
            showInProjectAction = new VcsTreeActions.ShowInProjectAction();
            rollbackAction = new VcsTreeActions.RollbackAction();
            toolbarActions = Collections.singletonList(selectOpenedFileAction);
        }
    }

    /**
     * Constructor for MySimpleChangesBrowser.
     * This MUST be called from the EDT, but only AFTER all slow operations
     * have been completed in background threads.
     */
    private MySimpleChangesBrowser(@NotNull Project project, @NotNull Collection<? extends Change> preparedChanges) {
        super(project, false, true);
        this.myProject = project;
        setChangesToDisplay(preparedChanges);

        // Add mouse listener for single-click preview functionality
        addSingleClickPreviewSupport();
    }

    @Override
    protected @NotNull List<AnAction> createPopupMenuActions() {
        initializeActions();
        // Include parent actions (which provide diff functionality) plus our custom actions
        List<AnAction> actions = new ArrayList<>(super.createPopupMenuActions());
        actions.add(showInProjectAction);
        actions.add(rollbackAction);
        return actions;
    }

    @Override
    protected @NotNull List<AnAction> createToolbarActions() {
        initializeActions();
        // Return the SAME instance-level list every time to prevent toolbar recreation
        return toolbarActions;
    }

    /**
     * Adds mouse listener to support single-click preview functionality
     */
    private void addSingleClickPreviewSupport() {
        // Get the changes viewer component (usually a JTree or JList)
        JComponent viewerComponent = getViewer();
        viewerComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return; // Only handle left clicks
                }

                if (e.getClickCount() == 1) {
                    
                    // Check if Shift or Ctrl is pressed - if so, skip opening file
                    if (e.isShiftDown() || e.isControlDown()) {
                        return; // Let the default selection behavior handle it
                    }

                    Change[] selectedChanges = getSelectedChanges().toArray(new Change[0]);

                    if (!uiSettings.getOpenInPreviewTabIfPossible()) {
                        return;
                    }

                    if (selectedChanges.length > 0) {
                        Change selectedChange = selectedChanges[0];
                        VirtualFile file = selectedChange.getVirtualFile();
                        if (file != null) {
                            // Single click: try to open in preview tab, do nothing if it fails
                            openInPreviewTab(myProject, file);
                        }
                    }
                }
            }
        });
    }

    /**
     * Tries to open a file in preview tab using reflection. If it fails, does nothing.
     */
    private void openInPreviewTab(Project project, VirtualFile file) {
        try {
            FileEditorManager editorManager = FileEditorManager.getInstance(project);

            // TODO: Reflection used to get access to preview tab method

            // Use reflection to create FileEditorOpenOptions
            Class<?> optionsClass = Class.forName("com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions");
            Object options = optionsClass.getDeclaredConstructor().newInstance();

            // Chain the methods using reflection
            Method withRequestFocus = optionsClass.getMethod("withRequestFocus", boolean.class);
            Method withUsePreviewTab = optionsClass.getMethod("withUsePreviewTab", boolean.class);
            Method withReuseOpen = optionsClass.getMethod("withReuseOpen", boolean.class);

            options = withRequestFocus.invoke(options, false);
            options = withUsePreviewTab.invoke(options, true);
            options = withReuseOpen.invoke(options, true);

            // Look for the openFile method with FileEditorOpenOptions
            Method openFileMethod = getMethod(editorManager, optionsClass);

            if (openFileMethod != null) {
                openFileMethod.setAccessible(true);
                openFileMethod.invoke(editorManager, file, null, options);
            } else {
                LOG.debug("Preview tab method not found, doing nothing for single click");
            }

        } catch (Exception e) {
            LOG.debug("Preview tab opening failed, doing nothing for single click", e);
        }
    }

    private static @Nullable Method getMethod(FileEditorManager editorManager, Class<?> optionsClass) {
        Method openFileMethod = null;
        Class<?> currentClass = editorManager.getClass();

        while (currentClass != null && openFileMethod == null) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if ("openFile".equals(method.getName())) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 3 &&
                            VirtualFile.class.isAssignableFrom(paramTypes[0]) &&
                            optionsClass.isAssignableFrom(paramTypes[2])) {
                        openFileMethod = method;
                        break;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return openFileMethod;
    }

    /**
     * Factory method that creates a MySimpleChangesBrowser instance asynchronously.
     * This properly handles slow operations by performing them in a background thread
     * before safely creating the component on the EDT.
     *
     * @param project The project
     * @param changes The changes to display
     * @return A CompletableFuture that will complete with the created browser component
     */
    public static CompletableFuture<MySimpleChangesBrowser> createAsync(@NotNull Project project,
                                                                        @NotNull VcsTree vcsTree,
                                                                        @NotNull Collection<? extends Change> changes) {
        CompletableFuture<MySimpleChangesBrowser> future = new CompletableFuture<>();
        if (project.isDisposed()) {
            future.completeExceptionally(new IllegalStateException("Project disposed"));
            return future;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                if (project.isDisposed()) {
                    future.completeExceptionally(new IllegalStateException("Project disposed"));
                    return;
                }
                // No pre-processing: SimpleAsyncChangesBrowser will handle async work internally
                MySimpleChangesBrowser browser = new MySimpleChangesBrowser(project, changes);
                future.complete(browser);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    /**
     * Opens a file and scrolls to the specified line, with option for preview tab
     *
     * @param project The project
     * @param file The file to open
     * @param line The line number to scroll to (-1 for no specific line)
     * @param isPreview Whether to open in preview tab
     */
    public void openAndScrollToChanges(Project project, VirtualFile file, int line, boolean isPreview) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            FileEditor[] editors;

            if (isPreview) {
                // For preview, we don't have a reliable fallback, so just use the reflection method
                // which we already call in openInPreviewTab. Here we'll just use standard API.
                editors = FileEditorManager.getInstance(project).openFile(file, true);
                LOG.debug("Opened file (fallback to regular tab): " + file.getName());
            } else {
                // Use standard API for regular tabs
                editors = FileEditorManager.getInstance(project).openFile(file, true);
                LOG.debug("Opened file in regular tab: " + file.getName());
            }

            // Scroll to specific line if provided
            for (FileEditor fileEditor : editors) {
                if (fileEditor instanceof TextEditor) {
                    Editor editor = ((TextEditor) fileEditor).getEditor();

                    // Move caret to the specific line if needed
                    if (line > 0) {
                        LogicalPosition pos = new LogicalPosition(line - 1, 0);
                        editor.getCaretModel().moveToLogicalPosition(pos);
                    }

                    // Center the view on caret
                    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                }
            }
        });
    }

    @Override
    protected void onDoubleClick() {
        // Handle double-click to open in regular/permanent tab
        Change[] selectedChanges = getSelectedChanges().toArray(new Change[0]);
        if (selectedChanges.length > 0) {
            Change selectedChange = selectedChanges[0];
            VirtualFile file = selectedChange.getVirtualFile();
            if (file != null) {
                // Double-click: open in regular (permanent) tab
                openAndScrollToChanges(myProject, file, -1, false);
                LOG.debug("Double-click: opened in permanent tab: " + file.getName());
            }
        }
    }
}