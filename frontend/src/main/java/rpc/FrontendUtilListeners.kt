package rpc

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class FrontendUtilSubscriptions(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    init {
        coroutineScope.launch {
            durable {
                UtilRpcApi.getInstance()
                    .getCommands(project.projectId())
                    .collect { cmd -> handleCommand(cmd) }
            }
        }

        // Report the frontend's current "Enable preview tab" setting to the backend, and keep it
        // updated. In split mode the backend cannot read this setting reliably (it is not synced),
        // so it relies on this pushed value to decide whether single-click opens a file.
        pushPreviewTabEnabled(UISettings.getInstance().openInPreviewTabIfPossible)
        ApplicationManager.getApplication().messageBus.connect(coroutineScope)
            .subscribe(UISettingsListener.TOPIC, UISettingsListener { settings ->
                pushPreviewTabEnabled(settings.openInPreviewTabIfPossible)
            })
    }

    private fun pushPreviewTabEnabled(enabled: Boolean) {
        coroutineScope.launch {
            try {
                UtilRpcApi.getInstance().setPreviewTabEnabled(project.projectId(), enabled)
            } catch (e: Throwable) {
                // best-effort; backend falls back to its own default if never received
            }
        }
    }

    private fun handleCommand(cmd: UtilCommand) {
        ApplicationManager.getApplication().invokeLater {
            when (cmd) {
                is UtilCommand.SelectInProject -> selectInProject(cmd.filePath)
            }
        }
    }

    private fun selectInProject(filePath: String) {
        val file = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return
        tw.activate({
            ProjectView.getInstance(project).select(null, file, true)
        }, true, true)
    }
}

class FrontendUtilSubscriptionsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FrontendUtilSubscriptions>()
    }
}
