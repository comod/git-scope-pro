package gitscope.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import rpc.ChangeNavDirection
import rpc.UtilRpcApi
import system.Defs

/**
 * Frontend actions for navigating Git Scope changes with keyboard shortcuts.
 *
 * The keystroke is handled on the frontend (where the editor and caret live). Each action reads the
 * focused editor's file and caret line, then delegates to the backend over [UtilRpcApi.navigateChange]:
 * the backend holds the authoritative scope change set and performs the canonical file open + caret
 * placement (honoring the preview-tab setting), so the same code path works in monolith and split mode.
 */
sealed class ChangeNavAction(private val direction: ChangeNavDirection) : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        var filePath: String? = null
        var caretLine = 0
        if (editor != null) {
            caretLine = editor.caretModel.logicalPosition.line
            val file = FileDocumentManager.getInstance().getFile(editor.document)
            filePath = file?.path
        }

        val pid = project.projectId()
        val cs = project.service<ChangeNavCoroutineScopeHolder>().scope
        cs.launch {
            try {
                UtilRpcApi.getInstance().navigateChange(pid, filePath, caretLine, direction)
            } catch (t: Throwable) {
                LOG.warn("ChangeNav: navigateChange failed", t)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Available whenever a project is open; the backend decides whether there is anywhere to go.
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        private val LOG: Logger = Defs.getLogger(ChangeNavAction::class.java)
    }
}

class NextChangeAction : ChangeNavAction(ChangeNavDirection.NEXT_CHANGE)
class PreviousChangeAction : ChangeNavAction(ChangeNavDirection.PREVIOUS_CHANGE)
class NextChangedFileAction : ChangeNavAction(ChangeNavDirection.NEXT_FILE)
class PreviousChangedFileAction : ChangeNavAction(ChangeNavDirection.PREVIOUS_FILE)

/**
 * Shows the Git Scope diff (base scope revision vs. current working content) for the focused file
 * as an editor tab — the same diff reachable from the tool window's right-click "Show Diff". The
 * diff is built on the backend (which holds the scope change set); this action just reports the
 * focused file and delegates.
 */
class ShowDiffAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val filePath = editor?.let { FileDocumentManager.getInstance().getFile(it.document)?.path }

        val pid = project.projectId()
        val cs = project.service<ChangeNavCoroutineScopeHolder>().scope
        cs.launch {
            try {
                UtilRpcApi.getInstance().showDiff(pid, filePath)
            } catch (t: Throwable) {
                LOG.warn("ShowDiff: showDiff failed", t)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        private val LOG: Logger = Defs.getLogger(ShowDiffAction::class.java)
    }
}

/**
 * Closes the active diff editor tab (only when the currently selected editor is a diff), leaving
 * other diff tabs untouched. Runs entirely on the frontend, where the editor tabs live.
 */
class CloseDiffAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        val selected = fem.selectedEditor?.file ?: return
        if (selected is com.intellij.diff.editor.DiffContentVirtualFile) {
            fem.closeFile(selected)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project != null &&
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                .selectedEditor?.file is com.intellij.diff.editor.DiffContentVirtualFile
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

/**
 * Project-level coroutine scope holder for firing the navigation RPC off the EDT. The platform
 * injects a scope tied to the project/plugin lifecycle.
 */
@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
class ChangeNavCoroutineScopeHolder(val scope: CoroutineScope)
