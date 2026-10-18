package utils;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Shared helper for opening a file the way Project View does and optionally placing the caret on
 * a specific line.
 *
 * <p>Goes through {@link OpenFileDescriptor#navigate}: the synchronous FileEditorManager open
 * methods are deprecated as of 2026.3, and their FileEditorOpenRequest replacements are
 * experimental and changed shape between 2026.2 and 2026.3. The Git Scope tool window runs on the
 * backend under the connected client's ClientId (not local) in split mode, so navigation reaches
 * the same per-client FileEditorManager that Project View uses: full editor initialization
 * (language parsing), the IDE VCS gutter and the plugin's own scope gutter, and reuse of the
 * existing tab instead of a duplicate.
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
        open(project, file, line, requestFocus, true);
    }

    /**
     * Opens the file in a permanent tab and focuses it, as a Project View double-click does.
     * {@code line} is 0-based; -1 leaves the caret where it was.
     */
    public static void openPermanent(@NotNull Project project, @NotNull VirtualFile file, int line) {
        open(project, file, line, true, false);
    }

    private static void open(@NotNull Project project, @NotNull VirtualFile file, int line,
                             boolean requestFocus, boolean usePreviewTab) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !file.isValid()) return;

            OpenFileDescriptor descriptor = line >= 0
                    ? new OpenFileDescriptor(project, file, line, 0)
                    : new OpenFileDescriptor(project, file);
            descriptor.setUsePreviewTab(usePreviewTab);
            descriptor.setScrollType(ScrollType.CENTER);
            descriptor.navigate(requestFocus);
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
