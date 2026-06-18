package rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class NavigationCommand(val filePath: String)

@Rpc
interface UtilRpcApi : RemoteApi<Unit> {
    suspend fun getNavigationCommands(projectId: ProjectId): Flow<NavigationCommand>

    companion object {
        suspend fun getInstance(): UtilRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<UtilRpcApi>())
        }
    }
}
