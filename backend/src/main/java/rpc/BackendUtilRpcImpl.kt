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
}

class BackendUtilRpcProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<UtilRpcApi>()) {
            BackendUtilRpcImpl()
        }
    }
}
