package rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import implementation.gutter.Range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import service.GutterDataService
import settings.GitScopeSettings

@Service(Service.Level.PROJECT)
class FrontendGutterSubscriptions(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    init {
        // Only subscribe via RPC in split mode — in monolithic mode,
        // GutterRenderingService listens to GutterDataService directly.
        if (!com.intellij.platform.ide.productMode.IdeProductMode.isMonolith) {
            coroutineScope.launch {
                durable {
                    GutterRpcApi.getInstance()
                        .getGutterUpdates(project.projectId())
                        .collect { event -> handleEvent(event) }
                }
            }
        }
    }

    private fun handleEvent(event: GutterUpdateEvent) {
        val gds = project.service<GutterDataService>()
        when (event) {
            is GutterUpdateEvent.DataUpdated -> {
                val dto = event.data
                gds.scopeDisplayName = dto.scopeDisplayName
                GitScopeSettings.getInstance().isSeparateGutterRendering = dto.separateGutterRendering
                val data = GutterDataService.GutterFileData(
                    dto.ranges.map { Range(it.line1, it.line2, it.vcsLine1, it.vcsLine2) },
                    dto.baseContent,
                    dto.headContent,
                    dto.scopeRanges?.map { Range(it.line1, it.line2, it.vcsLine1, it.vcsLine2) }
                )
                gds.publish(dto.filePath, data)
            }
            is GutterUpdateEvent.DataCleared -> gds.clear(event.filePath)
            is GutterUpdateEvent.AllCleared -> gds.clearAll()
        }
    }
}

class FrontendGutterSubscriptionsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FrontendGutterSubscriptions>()
    }
}
