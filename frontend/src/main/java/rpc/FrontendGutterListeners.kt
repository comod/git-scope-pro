package rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import implementation.gutter.Range
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import service.GutterDataService
import settings.GitScopeSettings
import system.Defs

@Service(Service.Level.PROJECT)
class FrontendGutterSubscriptions(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private val LOG = Defs.getLogger(FrontendGutterSubscriptions::class.java)

        /**
         * Delay before re-subscribing after the stream ended without an RPC failure. Two ways here:
         * the backend answered with an empty flow because it does not know the project yet (a race
         * won by slow backends — the subscription used to die silently for good in that case), or a
         * non-RPC error escaped, which `durable` deliberately does not retry.
         */
        private const val RESUBSCRIBE_DELAY_MS = 5_000L
    }

    init {
        // Only subscribe to gutter updates via RPC in split mode — in monolith,
        // the frontend GutterDataService IS the backend GutterDataService (same instance),
        // so re-publishing would cause an infinite loop.
        if (!com.intellij.platform.ide.productMode.IdeProductMode.isMonolith) {
            coroutineScope.launch {
                while (isActive) {
                    try {
                        durable {
                            GutterRpcApi.getInstance()
                                .getGutterUpdates(project.projectId())
                                .collect { event ->
                                    // One bad event must not kill the subscription: the stream is
                                    // the only source of gutter data, so letting an exception
                                    // propagate here would leave the gutter permanently empty.
                                    try {
                                        handleEvent(event)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (t: Throwable) {
                                        LOG.warn("Failed to apply gutter update, skipping event", t)
                                    }
                                }
                        }
                        LOG.debug("Gutter update stream completed, re-subscribing")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        // durable already retries RPC-level failures with its own backoff; anything
                        // arriving here is unexpected, so log it visibly and keep the subscription alive.
                        LOG.warn("Gutter update subscription failed, re-subscribing", t)
                    }
                    delay(RESUBSCRIBE_DELAY_MS)
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

                // Contents are omitted when unchanged since the backend's last message for this
                // file; the previously published data then carries them.
                val baseContent: String
                val headContent: String?
                if (dto.contentsIncluded) {
                    baseContent = dto.baseContent
                        ?: run {
                            LOG.warn("Gutter update for ${dto.filePath} claims contents but carries none, skipping")
                            return
                        }
                    headContent = dto.headContent
                } else {
                    val cached = gds.getData(dto.filePath)
                    if (cached == null) {
                        // Should not happen: the backend only omits contents it already sent within
                        // this subscription, and clears that memory together with AllCleared.
                        LOG.warn("Gutter update for ${dto.filePath} references cached contents that are missing, skipping")
                        return
                    }
                    baseContent = cached.baseContent
                    headContent = cached.headContent
                }

                val data = GutterDataService.GutterFileData(
                    dto.ranges.map { Range(it.line1, it.line2, it.vcsLine1, it.vcsLine2) },
                    baseContent,
                    headContent,
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
