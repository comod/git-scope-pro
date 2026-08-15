package rpc

import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import service.GutterDataService
import settings.GitScopeSettings

class BackendGutterRpcImpl : GutterRpcApi {

    /**
     * Streams gutter updates with conflation by file.
     *
     * <p>The RPC transport applies credit-based backpressure: a slow frontend or network stops the
     * stream from being drained. Listener callbacks therefore never push payloads into the stream —
     * they only mark a file dirty and wake the emit loop, which reads the <em>latest</em> snapshot
     * from [GutterDataService] at send time and emits with a suspending call. Under backpressure,
     * intermediate states of a file collapse into the newest one instead of being dropped (the
     * previous implementation lost events irrecoverably via {@code trySend} once its 64-element
     * buffer filled), memory is bounded at one pending entry per file, and the final state always
     * arrives.
     *
     * <p>The initial replay goes through the same dirty set, so a reconnecting consumer (the
     * frontend re-subscribes via {@code durable} after every connection loss) never queues more
     * than one snapshot per file no matter how often the link drops.
     */
    override suspend fun getGutterUpdates(projectId: ProjectId): Flow<GutterUpdateEvent> {
        val project = projectId.findProjectOrNull() ?: return emptyFlow()
        val gds = project.service<GutterDataService>()

        return flow {
            // Conflated: always accepts, coalesces repeated wake-ups; listeners never block or fail.
            val signal = Channel<Unit>(Channel.CONFLATED)
            val lock = Any()
            var allCleared = false
            val dirty = LinkedHashSet<String>()

            fun markDirty(filePath: String?) {
                synchronized(lock) {
                    if (filePath == null) {
                        allCleared = true
                        // Dirt older than the clear is obsolete; later updates re-add themselves.
                        dirty.clear()
                    } else {
                        dirty.add(filePath)
                    }
                }
                signal.trySend(Unit)
            }

            val listener = object : GutterDataService.Listener {
                override fun onDataUpdated(filePath: String, data: GutterDataService.GutterFileData) =
                    markDirty(filePath)

                override fun onDataCleared(filePath: String) = markDirty(filePath)

                override fun onAllCleared() = markDirty(null)
            }
            // Register the listener before the replay so no update between replay and registration
            // is missed; a double-send of the same file is harmless (the frontend overwrites).
            gds.addListener(listener)
            try {
                for (path in gds.getAllData().keys) {
                    markDirty(path)
                }

                while (true) {
                    signal.receive()
                    while (true) {
                        val clearAll: Boolean
                        val paths: List<String>
                        synchronized(lock) {
                            clearAll = allCleared
                            allCleared = false
                            paths = dirty.toList()
                            dirty.clear()
                        }
                        if (!clearAll && paths.isEmpty()) break

                        if (clearAll) {
                            emit(GutterUpdateEvent.AllCleared)
                        }
                        for (path in paths) {
                            // Read the freshest snapshot at send time, not at event time. A file
                            // cleared while queued yields null and becomes a DataCleared.
                            val data = gds.getData(path)
                            emit(
                                if (data != null) GutterUpdateEvent.DataUpdated(data.toDto(path, gds.scopeDisplayName))
                                else GutterUpdateEvent.DataCleared(path)
                            )
                        }
                    }
                }
            } finally {
                gds.removeListener(listener)
            }
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
