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
}

@Rpc
interface UtilRpcApi : RemoteApi<Unit> {
    suspend fun getCommands(projectId: ProjectId): Flow<UtilCommand>

    /**
     * Reports the frontend's "Enable preview tab" (UISettings.openInPreviewTabIfPossible) value to
     * the backend. In split/remote mode the backend's own UISettings is not synced with the
     * frontend, so the backend caches this pushed value to decide whether single-click should open
     * a file.
     */
    suspend fun setPreviewTabEnabled(projectId: ProjectId, enabled: Boolean)

    companion object {
        suspend fun getInstance(): UtilRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<UtilRpcApi>())
        }
    }
}
