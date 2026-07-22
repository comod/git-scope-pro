package utils;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorOpenRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Shared helper for opening a file through the platform's canonical FileEditorManager path and
 * optionally placing the caret on a specific line.
 *
 * <p>The Git Scope tool window runs on the backend under the connected client's ClientId (not
 * local) in split mode, so {@link FileEditorManagerEx#openFile} delegates to the same per-client
 * FileEditorManager that Project View uses: full editor initialization (language parsing), the IDE
 * VCS gutter and the plugin's own scope gutter, and reuse of the existing tab instead of a
 * duplicate. {@code usePreviewTab} is requested for forward compatibility (see the preview-tab
 * notes in the single-click handler).
 */
public final class FileOpener {

    private FileOpener() {
    }

    /**
     * Opens the file (reusing an existing tab) and, when {@code line >= 0}, moves the caret to that
     * 0-based line and scrolls it into view. Must be safe to call from any thread; the actual open
     * is scheduled on the EDT. Does not request focus.
     */
    public static void openAndGoToLine(@NotNull Project project, @NotNull VirtualFile file, int line) {
        openAndGoToLine(project, file, line, false);
    }

    /**
     * Variant that can request focus for the opened editor. Navigation actions pass
     * {@code requestFocus=true} so the editor becomes the focus owner and reports an accurate
     * caret position for subsequent navigation keystrokes. Requesting focus does not promote a
     * preview tab to a permanent one (only double-click / pin / modification does).
     */
    public static void openAndGoToLine(@NotNull Project project, @NotNull VirtualFile file, int line, boolean requestFocus) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !file.isValid()) return;

            FileEditorOpenRequest request = new FileEditorOpenRequest()
                    .withUsePreviewTab(true)
                    .withReuseOpen(true)
                    .withRequestFocus(requestFocus);
            var composite = FileEditorManagerEx.getInstanceEx(project).openFile(file, request);

            if (line >= 0) {
                for (FileEditor fileEditor : composite.getAllEditors()) {
                    if (fileEditor instanceof TextEditor) {
                        Editor editor = ((TextEditor) fileEditor).getEditor();
                        int lineCount = editor.getDocument().getLineCount();
                        int target = Math.max(0, Math.min(line, Math.max(0, lineCount - 1)));
                        LogicalPosition pos = new LogicalPosition(target, 0);
                        editor.getCaretModel().moveToLogicalPosition(pos);
                        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                    }
                }
            }
        });
    }

    /**
     * Whether single-click / navigation should open a file in a preview tab, based on the
     * "Enable preview tab" setting. In monolith mode the backend UISettings is authoritative; in
     * split mode it is not synced, so the caller supplies the frontend-reported value.
     *
     * @param frontendReported the value pushed by the frontend, or null if not reported yet
     */
    public static boolean isPreviewTabEnabled(Boolean frontendReported) {
        if (com.intellij.platform.ide.productMode.IdeProductMode.isMonolith()) {
            return UISettings.getInstance().getOpenInPreviewTabIfPossible();
        }
        if (frontendReported != null) {
            return frontendReported;
        }
        return UISettings.getInstance().getOpenInPreviewTabIfPossible();
    }
}
