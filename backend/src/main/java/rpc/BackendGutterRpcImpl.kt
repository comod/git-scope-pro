package rpc

import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import service.GutterDataService
import settings.GitScopeSettings

class BackendGutterRpcImpl : GutterRpcApi {
    override suspend fun getGutterUpdates(projectId: ProjectId): Flow<GutterUpdateEvent> {
        val project = projectId.findProjectOrNull() ?: return kotlinx.coroutines.flow.emptyFlow()
        val gds = project.service<GutterDataService>()

        return callbackFlow {
            // Register listener FIRST to avoid missing events during replay
            val listener = object : GutterDataService.Listener {
                override fun onDataUpdated(filePath: String, data: GutterDataService.GutterFileData) {
                    trySend(GutterUpdateEvent.DataUpdated(data.toDto(filePath, gds.scopeDisplayName)))
                }

                override fun onDataCleared(filePath: String) {
                    trySend(GutterUpdateEvent.DataCleared(filePath))
                }

                override fun onAllCleared() {
                    trySend(GutterUpdateEvent.AllCleared)
                }
            }
            gds.addListener(listener)

            // Then replay all current data (duplicates are harmless — frontend overwrites)
            for (entry in gds.getAllData().entries) {
                send(GutterUpdateEvent.DataUpdated(entry.value.toDto(entry.key, gds.scopeDisplayName)))
            }

            awaitClose { gds.removeListener(listener) }
        }
    }

    private fun GutterDataService.GutterFileData.toDto(filePath: String, scopeDisplayName: String) =
        GutterFileDataDto(
            filePath = filePath,
            ranges = ranges.map { GutterRangeDto(it.line1, it.line2, it.vcsLine1, it.vcsLine2) },
            baseContent = baseContent,
            headContent = headContent,
            scopeRanges = scopeRanges?.map { GutterRangeDto(it.line1, it.line2, it.vcsLine1, it.vcsLine2) },
            scopeDisplayName = scopeDisplayName,
            separateGutterRendering = GitScopeSettings.getInstance().isSeparateGutterRendering
        )
}

class BackendGutterRpcProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<GutterRpcApi>()) {
            BackendGutterRpcImpl()
        }
    }
}
