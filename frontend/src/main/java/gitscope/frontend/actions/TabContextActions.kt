package gitscope.frontend.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.launch
import rpc.FrontendTabStateService
import rpc.TabMoveDirection
import rpc.UtilRpcApi
import system.Defs

/**
 * Tab context-menu actions for the Git Scope tool window, registered on the frontend.
 *
 * <p>Registered here for both modes. In split mode the tab strip is rendered by the frontend, so
 * its context menu is built from the frontend's action registry and backend-registered actions
 * never appear in it — which is why renaming, resetting and moving tabs were all missing over
 * Remote Development. Monolith loads this module too, so one registration serves both and there is
 * no second implementation to keep in step.
 *
 * <p>The tool window, its ContentManager and the scope models are backend state, so these actions
 * decide enablement from what is visible here (tab index and label) and delegate the operation over
 * [UtilRpcApi] to TabActionService, which owns the rules and re-validates every request.
 */
sealed class TabContextAction : ToolWindowContextMenuActionBase() {

    final override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
        e.presentation.isEnabledAndVisible = false

        if (e.project == null || toolWindow.id != Defs.TOOL_WINDOW_NAME || content == null) return

        val contentManager = toolWindow.contentManager
        val index = contentManager.getIndexOfContent(content)
        if (index < 0) return

        e.presentation.isEnabledAndVisible = isEnabled(e.project!!, contentManager, index, content)
    }

    final override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
        val project = e.project ?: return
        if (content == null) return

        val index = toolWindow.contentManager.getIndexOfContent(content)
        if (index < 0) return

        perform(project, index, content)
    }

    protected abstract fun isEnabled(project: Project, contentManager: ContentManager, index: Int, content: Content): Boolean

    protected abstract fun perform(project: Project, index: Int, content: Content)

    /** Tab is neither the HEAD tab (index 0) nor the trailing "+" tab. */
    protected fun isRegularTab(index: Int, content: Content): Boolean =
        index > 0 && content.tabName != Defs.PLUS_TAB_LABEL

    protected fun sendToBackend(project: Project, description: String, call: suspend (UtilRpcApi, ProjectId) -> Unit) {
        // Resolve the project id on the caller's (EDT) side, like the navigation actions do.
        val projectId = project.projectId()
        project.service<ChangeNavCoroutineScopeHolder>().scope.launch {
            try {
                call(UtilRpcApi.getInstance(), projectId)
            } catch (t: Throwable) {
                LOG.warn("Tab action '$description' failed", t)
            }
        }
    }

    companion object {
        @JvmStatic
        protected val LOG: Logger = Defs.getLogger(TabContextAction::class.java)
    }
}

class FrontendRenameTabAction : TabContextAction() {
    override fun isEnabled(project: Project, contentManager: ContentManager, index: Int, content: Content) =
        isRegularTab(index, content)

    override fun perform(project: Project, index: Int, content: Content) {
        // Prompt on the frontend, where the UI is, then send only the result over RPC.
        val newName = Messages.showInputDialog(
            project,
            "Enter new tab name:",
            "Rename Tab",
            Messages.getQuestionIcon(),
            content.displayName,
            null
        )
        if (newName.isNullOrEmpty()) return

        sendToBackend(project, "rename") { api, pid -> api.renameTab(pid, index, newName) }
    }
}

class FrontendResetTabNameAction : TabContextAction() {
    // Whether a tab carries a custom name is backend model state, mirrored by
    // FrontendTabStateService. While that state is still unknown the action stays enabled and the
    // backend no-ops if there is nothing to reset.
    override fun isEnabled(project: Project, contentManager: ContentManager, index: Int, content: Content) =
        isRegularTab(index, content) &&
            project.service<FrontendTabStateService>().hasCustomNameOrUnknown(index)

    override fun perform(project: Project, index: Int, content: Content) {
        sendToBackend(project, "reset name") { api, pid -> api.resetTabName(pid, index) }
    }
}

class FrontendMoveTabLeftAction : TabContextAction() {
    override fun isEnabled(project: Project, contentManager: ContentManager, index: Int, content: Content) =
        isRegularTab(index, content) && index > 1

    override fun perform(project: Project, index: Int, content: Content) {
        sendToBackend(project, "move left") { api, pid -> api.moveTab(pid, index, TabMoveDirection.LEFT) }
    }
}

class FrontendMoveTabRightAction : TabContextAction() {
    override fun isEnabled(project: Project, contentManager: ContentManager, index: Int, content: Content) =
        isRegularTab(index, content) && index < contentManager.contentCount - 2

    override fun perform(project: Project, index: Int, content: Content) {
        sendToBackend(project, "move right") { api, pid -> api.moveTab(pid, index, TabMoveDirection.RIGHT) }
    }
}
