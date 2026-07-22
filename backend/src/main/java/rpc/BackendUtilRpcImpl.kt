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
}

class BackendUtilRpcProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<UtilRpcApi>()) {
            BackendUtilRpcImpl()
        }
    }
}
