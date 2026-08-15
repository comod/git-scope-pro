package rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import system.Defs

/**
 * Mirrors the backend's set of tabs that carry a custom name, so "Reset Tab Name" can be disabled
 * for tabs with nothing to reset.
 *
 * <p>Action update() cannot suspend, so the answer has to be available synchronously — hence a
 * pushed StateFlow cached here rather than a request per menu build, which would also put a network
 * round-trip in the way of opening a context menu.
 *
 * <p>Until the first value arrives the answer is "unknown", and callers treat unknown as enabled:
 * the worst case is then an action that runs and finds nothing to reset, never one that is missing.
 */
@Service(Service.Level.PROJECT)
class FrontendTabStateService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    @Volatile
    private var customNamedTabs: Set<Int>? = null

    init {
        coroutineScope.launch {
            while (isActive) {
                try {
                    durable {
                        UtilRpcApi.getInstance()
                            .getCustomNamedTabs(project.projectId())
                            .collect { indices ->
                                LOG.debug("Custom-named tabs: $indices")
                                customNamedTabs = indices.toSet()
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    LOG.warn("Tab state subscription failed, re-subscribing", t)
                }
                // Fall back to "unknown" so the menu stays usable while disconnected.
                customNamedTabs = null
                delay(RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    /** True when the tab is known to have a custom name, or when the state is not known yet. */
    fun hasCustomNameOrUnknown(tabIndex: Int): Boolean {
        val known = customNamedTabs ?: return true
        return tabIndex in known
    }

    companion object {
        private val LOG: Logger = Defs.getLogger(FrontendTabStateService::class.java)
        private const val RESUBSCRIBE_DELAY_MS = 5_000L
    }
}

class FrontendTabStateStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FrontendTabStateService>()
    }
}
