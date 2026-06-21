package rpc

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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
    }

    private fun handleCommand(cmd: UtilCommand) {
        ApplicationManager.getApplication().invokeLater {
            when (cmd) {
                is UtilCommand.SelectInProject -> selectInProject(cmd.filePath)
                is UtilCommand.OpenPreviewTab -> openPreviewTab(cmd.filePath, cmd.line)
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

    private fun openPreviewTab(filePath: String, line: Int) {
        val file = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return
        val descriptor = if (line >= 0) OpenFileDescriptor(project, file, line, 0) else OpenFileDescriptor(project, file)
        descriptor.setUsePreviewTab(true)
        FileEditorManager.getInstance(project).openEditor(descriptor, true)
    }
}

class FrontendUtilSubscriptionsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FrontendUtilSubscriptions>()
    }
}
