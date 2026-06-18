package rpc

import com.intellij.ide.projectView.ProjectView
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
                    .getNavigationCommands(project.projectId())
                    .collect { cmd -> handleNavigation(cmd) }
            }
        }
    }

    private fun handleNavigation(cmd: NavigationCommand) {
        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().findFileByPath(cmd.filePath) ?: return@invokeLater
            val tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return@invokeLater
            tw.activate({
                ProjectView.getInstance(project).select(null, file, true)
            }, true, true)
        }
    }
}

class FrontendUtilSubscriptionsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FrontendUtilSubscriptions>()
    }
}
