package implementation.lineStatusTracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Workaround for IntelliJ IDEA commit panel diff issue.
 * 
 * Problem: When viewing diffs in the commit panel, IDEA shows the diff between the current
 * working copy and the custom base revision set by Git Scope. This causes incorrect diffs
 * to be displayed - the commit panel should always show diffs against HEAD (the last commit),
 * not against a custom branch base.
 * 
 * Solution: This class detects when commit panel diff editors are active and temporarily
 * switches the base revision to HEAD. When the user switches away from the commit panel,
 * it restores the custom base revision.
 */
public class CommitDiffWorkaround implements Disposable {
    private static final Logger LOG = Defs.getLogger(CommitDiffWorkaround.class);
    private static final int ACTIVATION_DELAY_MS = 300;

    private final Project project;
    private final AtomicBoolean disposing = new AtomicBoolean(false);

    // Map: Document -> Set of commit diff Editors for that document
    private final Map<Document, Set<Editor>> commitDiffEditors = new HashMap<>();

    // Track which documents are currently showing HEAD base (active in commit diff)
    private final Set<Document> activeCommitDiffs = new HashSet<>();

    // Callback interface for base revision switching
    private final BaseRevisionSwitcher baseRevisionSwitcher;

    /**
     * Interface for switching base revisions.
     */
    public interface BaseRevisionSwitcher {
        /**
         * Switch to HEAD base revision for the given document.
         * @param document The document to switch
         * @param headContent The HEAD revision content
         */
        void switchToHeadBase(@NotNull Document document, @NotNull String headContent);

        /**
         * Switch to custom base revision for the given document.
         * @param document The document to switch
         * @param customContent The custom base revision content
         */
        void switchToCustomBase(@NotNull Document document, @NotNull String customContent);

        /**
         * Get the cached HEAD content for a document, or null if not cached.
         */
        @Nullable String getCachedHeadContent(@NotNull Document document);

        /**
         * Get the cached custom base content for a document, or null if not cached.
         */
        @Nullable String getCachedCustomBaseContent(@NotNull Document document);

        /**
         * Cache HEAD content for a document.
         */
        void cacheHeadContent(@NotNull Document document, @NotNull String headContent);

        /**
         * Check if document is tracked and held.
         */
        boolean isTracked(@NotNull Document document);

        /**
         * Mark document as showing HEAD base.
         */
        void markShowingHeadBase(@NotNull Document document, boolean showing);

        /**
         * Check if document is showing HEAD base.
         */
        boolean isShowingHeadBase(@NotNull Document document);
    }

    public CommitDiffWorkaround(@NotNull Project project, @NotNull BaseRevisionSwitcher switcher) {
        this.project = project;
        this.baseRevisionSwitcher = switcher;
    }

    @Override
    public void dispose() {
        disposing.set(true);
        synchronized (this) {
            commitDiffEditors.clear();
            activeCommitDiffs.clear();
        }
    }

    /**
     * Check if an editor is a commit panel diff editor.
     * Call this when a DIFF editor is created to determine if it needs special handling.
     */
    public boolean isCommitPanelDiff(@NotNull Editor editor) {
        if (editor.getEditorKind() != EditorKind.DIFF) {
            return false;
        }

        return isInCommitToolWindowHierarchy(editor);
    }

    /**
     * Handle commit panel diff editor created.
     * Call this when a commit panel diff editor is created.
     */
    public void handleCommitDiffEditorCreated(@NotNull Editor editor) {
        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);

        if (file == null) {
            return;
        }

        synchronized (this) {
            // Register the editor
            commitDiffEditors.computeIfAbsent(doc, k -> new HashSet<>()).add(editor);

            // Pre-cache HEAD content if tracked
            if (baseRevisionSwitcher.isTracked(doc)) {
                if (baseRevisionSwitcher.getCachedHeadContent(doc) == null) {
                    String headContent = fetchHeadRevisionContent(file);
                    if (headContent != null) {
                        baseRevisionSwitcher.cacheHeadContent(doc, headContent);
                    }
                }
            }
        }

        // Check if a commit diff editor is currently selected - if so, activate HEAD base
        scheduleActivationIfCommitDiffSelected();
    }

    /**
     * Handle commit panel diff editor released (closed).
     * Call this when a commit panel diff editor is closed.
     */
    public void handleCommitDiffEditorReleased(@NotNull Editor editor) {
        Document doc = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);

        if (file == null) {
            return;
        }

        synchronized (this) {
            // Unregister the editor
            Set<Editor> editors = commitDiffEditors.get(doc);
            if (editors != null) {
                editors.remove(editor);
                if (editors.isEmpty()) {
                    commitDiffEditors.remove(doc);
                }
            }

            // Remove from active tracking
            activeCommitDiffs.remove(doc);

            // Restore custom base if no longer showing in any commit diff
            if (!commitDiffEditors.containsKey(doc) && baseRevisionSwitcher.isShowingHeadBase(doc)) {
                restoreCustomBaseForDocument(doc);
            }
        }
    }

    /**
     * Handle editor selection changed to a commit diff editor.
     * Call this when the user switches to a commit panel diff tab.
     */
    public void handleSwitchedToCommitDiff() {
        // Delay activation to allow diff window to fully render before switching base
        com.intellij.util.Alarm alarm = new com.intellij.util.Alarm(this);
        alarm.addRequest(() -> {
            if (disposing.get()) return;
            activateHeadBaseForAllCommitDiffs();
        }, ACTIVATION_DELAY_MS);
    }

    /**
     * Handle editor selection changed away from a commit diff editor.
     * Call this when the user switches away from a commit panel diff tab.
     *
     * Note: This safely restores ALL tracked documents. Documents that still have
     * commit diff editors open will be skipped by restoreCustomBaseForDocument().
     */
    public void handleSwitchedAwayFromCommitDiff() {
        restoreCustomBaseForAllDocuments();
    }

    /**
     * Check if document should skip custom base update (because it's showing HEAD base).
     * Call this before applying a custom base update to check if it should be skipped.
     */
    public boolean shouldSkipCustomBaseUpdate(@NotNull Document document) {
        return baseRevisionSwitcher.isShowingHeadBase(document);
    }

    /**
     * Check if there are commit diff editors open for the given document.
     * Used to determine if a document's tracker should be kept alive.
     */
    public boolean hasCommitDiffEditorsFor(@NotNull Document document) {
        synchronized (this) {
            Set<Editor> editors = commitDiffEditors.get(document);
            return editors != null && !editors.isEmpty();
        }
    }

    // Private implementation methods

    private boolean isInCommitToolWindowHierarchy(@NotNull Editor editor) {
        try {
            Component parent = editor.getComponent();

            // Walk up component hierarchy looking for DiffRequestProcessor
            int depth = 0;
            while (parent != null && depth < 50) {
                if (parent.getClass().getName().contains("DiffRequestProcessor")) {
                    return true;
                }
                parent = parent.getParent();
                depth++;
            }
        } catch (Exception e) {
            LOG.warn("Error checking commit tool window hierarchy", e);
        }

        return false;
    }

    private void scheduleActivationIfCommitDiffSelected() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposing.get()) return;

            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            FileEditor selectedEditor = fileEditorManager.getSelectedEditor();

            if (selectedEditor != null &&
                selectedEditor.getClass().getSimpleName().equals("BackendDiffRequestProcessorEditor")) {
                // Delay activation to allow diff window to fully render
                com.intellij.util.Alarm alarm = new com.intellij.util.Alarm(this);
                alarm.addRequest(() -> {
                    if (disposing.get()) return;
                    activateHeadBaseForAllCommitDiffs();
                }, ACTIVATION_DELAY_MS);
            }
        });
    }

    /**
     * Activate HEAD base for all documents that have commit diff editors.
     *
     * Design note: This activates HEAD base for ALL documents with commit diffs, not just
     * the currently visible one. This is intentional - when viewing commit diffs, we enter
     * "commit review mode" where all files should show diffs against HEAD, not custom base.
     * This provides consistent behavior and avoids confusion when switching between files.
     *
     * The restoration logic (via hasCommitDiffEditorsFor checks) ensures that when commit
     * diffs are closed, only documents without any remaining commit diff editors get their
     * custom base restored.
     */
    private void activateHeadBaseForAllCommitDiffs() {
        synchronized (this) {
            for (Map.Entry<Document, Set<Editor>> entry : commitDiffEditors.entrySet()) {
                Document doc = entry.getKey();
                Set<Editor> diffEditors = entry.getValue();

                if (diffEditors.isEmpty()) {
                    continue;
                }

                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                if (file == null) {
                    continue;
                }

                if (!baseRevisionSwitcher.isTracked(doc)) {
                    continue;
                }

                if (baseRevisionSwitcher.isShowingHeadBase(doc)) {
                    continue;
                }

                // Get HEAD content (fetch if not cached)
                String headContent = baseRevisionSwitcher.getCachedHeadContent(doc);
                if (headContent == null) {
                    headContent = fetchHeadRevisionContent(file);
                    if (headContent != null) {
                        baseRevisionSwitcher.cacheHeadContent(doc, headContent);
                    }
                }

                if (headContent != null) {
                    baseRevisionSwitcher.markShowingHeadBase(doc, true);
                    activeCommitDiffs.add(doc);

                    String finalHeadContent = headContent;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (disposing.get()) return;
                        baseRevisionSwitcher.switchToHeadBase(doc, finalHeadContent);
                    });
                }
            }
        }
    }

    private void restoreCustomBaseForAllDocuments() {
        synchronized (this) {
            for (Document doc : new HashSet<>(activeCommitDiffs)) {
                restoreCustomBaseForDocument(doc);
            }
        }
    }

    private void restoreCustomBaseForDocument(@NotNull Document doc) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) {
            return;
        }

        if (!baseRevisionSwitcher.isShowingHeadBase(doc)) {
            return;
        }

        // Fix Hole #2: Don't restore if commit diff editors are still open for this document
        if (hasCommitDiffEditorsFor(doc)) {
            return;
        }

        String customContent = baseRevisionSwitcher.getCachedCustomBaseContent(doc);
        if (customContent != null) {
            baseRevisionSwitcher.markShowingHeadBase(doc, false);
            activeCommitDiffs.remove(doc);

            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposing.get()) return;
                baseRevisionSwitcher.switchToCustomBase(doc, customContent);
            });
        }
    }

    private String fetchHeadRevisionContent(@NotNull VirtualFile file) {
        try {
            if (project == null || project.isDisposed()) {
                return null;
            }

            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            Collection<Change> allChanges = changeListManager.getAllChanges();

            // Find the change for this file
            for (Change change : allChanges) {
                VirtualFile changeFile = change.getVirtualFile();
                if (changeFile != null && changeFile.equals(file)) {
                    // The "before" revision is HEAD
                    ContentRevision beforeRevision = change.getBeforeRevision();
                    if (beforeRevision != null) {
                        String content = beforeRevision.getContent();
                        if (content != null) {
                            return StringUtil.convertLineSeparators(content);
                        }
                    }
                }
            }

            // File is not modified - current content IS HEAD
            Document doc = FileDocumentManager.getInstance().getDocument(file);
            if (doc != null) {
                return doc.getText();
            }

            return null;
        } catch (Exception e) {
            LOG.warn("Error fetching HEAD revision content for " + file.getName(), e);
            return null;
        }
    }
}
