package toolwindow.elements;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import toolwindow.VcsTreeActions;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class MySimpleChangesBrowser extends SimpleAsyncChangesBrowser {
    private static final Logger LOG = Logger.getInstance(MySimpleChangesBrowser.class);
    private final Project myProject;
    UISettings uiSettings = UISettings.getInstance();

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
        List<AnAction> actions = new ArrayList<>(super.createPopupMenuActions());
        actions.add(new VcsTreeActions.ShowInProjectAction());
        actions.add(new VcsTreeActions.RollbackAction());
        return actions;
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

            if (openFileMethod != null) {
                openFileMethod.setAccessible(true);
                openFileMethod.invoke(editorManager, file, null, options);
                LOG.debug("Successfully opened file in preview tab: " + file.getName());
            } else {
                LOG.debug("Preview tab method not found, doing nothing for single click");
            }

        } catch (Exception e) {
            LOG.debug("Preview tab opening failed, doing nothing for single click", e);
        }
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
                                                                        @NotNull Collection<? extends Change> changes) {
        LOG.debug("Starting asynchronous creation of MySimpleChangesBrowser");

        // Track operation time for diagnostics
        long startTime = System.currentTimeMillis();

        // Check if project is already disposed
        if (project.isDisposed()) {
            LOG.debug("Project is already disposed, not creating browser");
            CompletableFuture<MySimpleChangesBrowser> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalStateException("Project disposed"));
            return failedFuture;
        }

        // Use application pool executor for background tasks
        Executor backgroundExecutor = AppExecutorUtil.getAppExecutorService();

        // Step 1: Pre-process all changes in background thread
        // This will create a completely prepared set of changes that won't trigger slow operations when used in the UI
        return CompletableFuture.supplyAsync(() -> {
                    LOG.debug("Preparing changes in background thread");
                    try {
                        // Make a defensive copy of changes
                        Collection<Change> fullyPreparedChanges = new ArrayList<>(changes.size());

                        // Pre-compute all necessary data in background thread to avoid EDT slow operations
                        for (Change change : changes) {
                            if (change != null) {
                                // Pre-load data completely in this thread
                                VirtualFile file = change.getVirtualFile();
                                if (file != null && file.isValid() && !project.isDisposed()) {
                                    // Touch the file path to ensure it's loaded
                                    String path = file.getPath();

                                    // Pre-load change path info
                                    ChangesUtil.getFilePath(change);
                                }

                                // Add the change after pre-loading data
                                fullyPreparedChanges.add(change);
                            }
                        }

                        LOG.debug("Completed preparation of " + fullyPreparedChanges.size() + " changes in background thread");
                        return fullyPreparedChanges;
                    } catch (Exception e) {
                        LOG.error("Error preparing changes", e);
                        throw new CompletionException(e);
                    }
                }, backgroundExecutor)
                // Step 2: Create the browser on EDT with fully prepared data
                .thenApplyAsync(preparedChanges -> {
                    try {
                        // Create browser on EDT, but with all data fully prepared
                        MySimpleChangesBrowser browser = new MySimpleChangesBrowser(project, preparedChanges);
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        LOG.debug("Browser component created successfully in " + elapsedTime + "ms");
                        return browser;
                    } catch (Exception e) {
                        LOG.error("Error creating browser component", e);
                        throw new RuntimeException(e);
                    }
                }, ApplicationManager.getApplication()::invokeLater);
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