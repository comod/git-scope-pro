package rpc

import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class BackendUtilRpcImpl : UtilRpcApi {
    override suspend fun getCommands(projectId: ProjectId): Flow<UtilCommand> {
        val project = projectId.findProjectOrNull() ?: return emptyFlow()
        return project.service<UtilCommandService>().commands
    }

    override suspend fun setPreviewTabEnabled(projectId: ProjectId, enabled: Boolean) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<UtilCommandService>().setPreviewTabEnabled(enabled)
    }

    override suspend fun navigateChange(
        projectId: ProjectId,
        currentFilePath: String?,
        caretLine: Int,
        direction: ChangeNavDirection
    ) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.ChangeNavigationService>()
            .navigate(currentFilePath, caretLine, direction)
    }

    override suspend fun showDiff(projectId: ProjectId, currentFilePath: String?) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.ChangeNavigationService>().showDiff(currentFilePath)
    }

    override suspend fun renameTab(projectId: ProjectId, tabIndex: Int, newName: String) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.TabActionService>().renameTab(tabIndex, newName)
    }

    override suspend fun resetTabName(projectId: ProjectId, tabIndex: Int) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.TabActionService>().resetTabName(tabIndex)
    }

    override suspend fun moveTab(projectId: ProjectId, tabIndex: Int, direction: TabMoveDirection) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.TabActionService>().moveTab(tabIndex, direction)
    }

    override suspend fun getRenamedTabs(projectId: ProjectId): Flow<Map<Int, String>> {
        val project = projectId.findProjectOrNull() ?: return emptyFlow()
        // Publish the current state before the subscriber starts collecting, so a frontend that
        // connects after the tabs were restored still gets the truth rather than the initial empty.
        project.service<service.TabActionService>().publishRenamedTabs()
        return project.service<UtilCommandService>().renamedTabs
    }

    override suspend fun fileOpened(projectId: ProjectId) {
        val project = projectId.findProjectOrNull() ?: return
        project.service<service.ViewService>().collectChanges(false)
    }
}

class BackendUtilRpcProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<UtilRpcApi>()) {
            BackendUtilRpcImpl()
        }
    }
}
