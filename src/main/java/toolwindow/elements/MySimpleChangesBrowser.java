package toolwindow.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class MySimpleChangesBrowser extends SimpleAsyncChangesBrowser {
    private static final Logger LOG = Logger.getInstance(MySimpleChangesBrowser.class);
    private final Project myProject;

    /**
     * Constructor for MySimpleChangesBrowser.
     * This MUST be called from the EDT, but only AFTER all slow operations
     * have been completed in background threads.
     */
    private MySimpleChangesBrowser(@NotNull Project project, @NotNull Collection<? extends Change> preparedChanges) {
        super(project, false, true);
        this.myProject = project;
        setChangesToDisplay(preparedChanges);
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

    public void openAndScrollToChanges(Project project, VirtualFile file, int line) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
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
        Change[] selectedChanges = getSelectedChanges().toArray(new Change[0]);
        if (selectedChanges.length > 0) {
            Change selectedChange = selectedChanges[0];
            VirtualFile file = selectedChange.getVirtualFile();
            if (file != null) {
                openAndScrollToChanges(myProject, file, -1); // or pass specific line number if known
            }
        }
    }
}