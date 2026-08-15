package rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import system.Defs

/**
 * Mirrors the backend's renamed tabs — tab index -> the branch-based name the tab would revert to.
 *
 * <p>Serves two purposes. "Reset Tab Name" is disabled for tabs with nothing to reset, and action
 * update() cannot suspend, so the answer has to be available synchronously — hence a pushed
 * StateFlow cached here rather than a request per menu build, which would also put a network
 * round-trip in the way of opening a context menu.
 *
 * <p>And the original name is applied as the tab's tooltip here, on the frontend's own Content
 * objects. The backend sets it too, but in split mode the tab strip is rendered against frontend
 * contents, and the description written on the backend copy never reaches the label. In monolith
 * both sides share the same Content, so this is simply an idempotent re-set.
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
    private var renamedTabs: Map<Int, String>? = null

    init {
        coroutineScope.launch {
            while (isActive) {
                try {
                    durable {
                        UtilRpcApi.getInstance()
                            .getRenamedTabs(project.projectId())
                            .collect { tabs ->
                                LOG.debug("Renamed tabs: $tabs")
                                renamedTabs = tabs
                                applyTooltips(tabs)
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    LOG.warn("Tab state subscription failed, re-subscribing", t)
                }
                // Fall back to "unknown" so the menu stays usable while disconnected.
                renamedTabs = null
                delay(RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    /** True when the tab is known to have a custom name, or when the state is not known yet. */
    fun hasCustomNameOrUnknown(tabIndex: Int): Boolean {
        val known = renamedTabs ?: return true
        return tabIndex in known
    }

    /**
     * Shows the original name as the tooltip of each renamed tab, and clears it elsewhere. The tab
     * label reads Content.getDescription(), and the tool window content UI refreshes it on the
     * resulting property change.
     */
    private fun applyTooltips(tabs: Map<Int, String>) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(Defs.TOOL_WINDOW_NAME)
                ?: return@invokeLater  // not created yet; the next publish re-applies
            val contentManager = toolWindow.contentManager

            for (index in 0 until contentManager.contentCount) {
                val content = contentManager.getContent(index) ?: continue
                val tooltip = tabs[index]
                if (content.description != tooltip) {
                    content.description = tooltip
                }
            }
        }
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
