package rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed class UtilCommand {
    @Serializable
    data class SelectInProject(val filePath: String) : UtilCommand()

    @Serializable
    data class OpenPreviewTab(val filePath: String, val line: Int = -1) : UtilCommand()
}

@Rpc
interface UtilRpcApi : RemoteApi<Unit> {
    suspend fun getCommands(projectId: ProjectId): Flow<UtilCommand>

    companion object {
        suspend fun getInstance(): UtilRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<UtilRpcApi>())
        }
    }
}
