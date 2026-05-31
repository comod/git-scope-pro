package rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed class GutterUpdateEvent {
    @Serializable
    data class DataUpdated(val data: GutterFileDataDto) : GutterUpdateEvent()

    @Serializable
    data class DataCleared(val filePath: String) : GutterUpdateEvent()

    @Serializable
    data object AllCleared : GutterUpdateEvent()
}

@Rpc
interface GutterRpcApi : RemoteApi<Unit> {
    suspend fun getGutterUpdates(projectId: ProjectId): Flow<GutterUpdateEvent>

    companion object {
        suspend fun getInstance(): GutterRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<GutterRpcApi>())
        }
    }
}
