package rpc

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.NativeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
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

        /* Report the frontend's current "Enable preview tab" setting to the backend, and keep it
           updated. In split mode the backend cannot read this setting reliably (it is not synced),
           so it relies on this pushed value to decide whether single-click opens a file. */
        pushPreviewTabEnabled(UISettings.getInstance().openInPreviewTabIfPossible)
        ApplicationManager.getApplication().messageBus.connect(coroutineScope)
            .subscribe(UISettingsListener.TOPIC, UISettingsListener { settings ->
                pushPreviewTabEnabled(settings.openInPreviewTabIfPossible)
            })

        /* Warms the backend's scope model on file open, in place of a backend-registered
           FileEditorManagerListener: open editors are frontend-owned state under Remote
           Development, so a backend listener for this topic never fires in a real split
           deployment. Deliberately does not bump the backend's apply generation -- doing so
           discarded the result of any in-flight fresh collection (e.g. one triggered by a
           just-finished rebase) and replaced it with this cache-served one, so the scope showed
           pre-operation state, stuck until the next tab switch (issue #78). collectChanges(false)
           only warms the model when nothing has been collected yet. */
        project.messageBus.connect(coroutineScope).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    notifyBackendFileOpened()
                }
            }
        )
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

    private fun notifyBackendFileOpened() {
        coroutineScope.launch {
            try {
                UtilRpcApi.getInstance().fileOpened(project.projectId())
            } catch (e: Throwable) {
                // best-effort; the backend just misses one warm-up opportunity
            }
        }
    }

    private fun handleCommand(cmd: UtilCommand) {
        ApplicationManager.getApplication().invokeLater {
            when (cmd) {
                is UtilCommand.SelectInProject -> selectInProject(cmd.filePath)
                is UtilCommand.OpenInAssociatedApplication ->
                    openInAssociatedApplication(cmd.filePath, cmd.fileName)
            }
        }
    }

    /**
     * Launches a file the editor cannot display in its associated application, on this machine.
     *
     * <p>[LocalFileSystem.findFileByPath] is the mode check, not just a lookup: it resolves against
     * the disk of the process that calls it. In monolith and local split mode that is the same
     * machine as the backend, so the path resolves and the launch reaches the user's desktop. Over
     * Remote Development the path belongs to the host, nothing resolves here, and there is no local
     * copy to hand to an application — so say that rather than fail silently.
     *
     * <p>(A file at the identical absolute path on both machines would resolve and open the client's
     * copy. Accepted: it needs the same checkout layout on both sides, and the fallback would be a
     * notification either way.)
     */
    private fun openInAssociatedApplication(filePath: String, fileName: String) {
        val localFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (localFile == null) {
            utils.Notification.info(
                project,
                "Cannot open $fileName externally",
                "The file is on the remote host, so it cannot be opened in an application on this machine."
            )
            return
        }
        // Returns false when the OS has no association or the launch failed; it logs the cause.
        if (!NativeFileType.openAssociatedApplication(localFile)) {
            utils.Notification.warn(
                project,
                "Could not open $fileName",
                "No application is associated with this file type, or the system refused to start it."
            )
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
