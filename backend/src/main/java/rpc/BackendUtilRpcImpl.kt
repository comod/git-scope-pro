package rpc

import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class BackendUtilRpcImpl : UtilRpcApi {
    override suspend fun getNavigationCommands(projectId: ProjectId): Flow<NavigationCommand> {
        val project = projectId.findProjectOrNull() ?: return emptyFlow()
        return project.service<NavigationCommandService>().commands
    }
}

class BackendUtilRpcProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<UtilRpcApi>()) {
            BackendUtilRpcImpl()
        }
    }
}
