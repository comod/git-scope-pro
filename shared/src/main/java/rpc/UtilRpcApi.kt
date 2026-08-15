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

/** Direction for change/file navigation actions. */
@Serializable
enum class ChangeNavDirection {
    NEXT_CHANGE,
    PREVIOUS_CHANGE,
    NEXT_FILE,
    PREVIOUS_FILE
}

/** Direction for moving a scope tab within the tool window. */
@Serializable
enum class TabMoveDirection {
    LEFT,
    RIGHT
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

    /**
     * Navigates to the next/previous change or changed file, relative to the given caret position
     * in the currently focused editor. Runs on the backend, which holds the authoritative scope
     * change set and performs the canonical file open + caret placement (honoring the preview-tab
     * setting). [currentFilePath] is null when no editor is focused; [caretLine] is 0-based.
     */
    suspend fun navigateChange(
        projectId: ProjectId,
        currentFilePath: String?,
        caretLine: Int,
        direction: ChangeNavDirection
    )

    /**
     * Shows the Git Scope diff for the focused file as an editor tab (same diff as the tool
     * window's right-click "Show Diff"). Runs on the backend, which holds the scope change set.
     */
    suspend fun showDiff(projectId: ProjectId, currentFilePath: String?)

    /**
     * Tab operations for the tool window's tab context menu, addressed by tab index.
     *
     * <p>In split mode the tab strip is rendered by the frontend, so the context menu is built from
     * the frontend's action registry — backend-registered actions never appear in it. The frontend
     * actions therefore delegate here, because the tool window, its ContentManager and the scope
     * models all live on the backend. All three are no-ops when the index does not address a
     * renameable/movable tab, so the backend stays authoritative over the rules.
     */
    suspend fun renameTab(projectId: ProjectId, tabIndex: Int, newName: String)

    suspend fun resetTabName(projectId: ProjectId, tabIndex: Int)

    suspend fun moveTab(projectId: ProjectId, tabIndex: Int, direction: TabMoveDirection)

    /**
     * Indices of tabs that currently carry a custom name, so "Reset Tab Name" can be disabled for
     * tabs that have nothing to reset. Whether a tab was renamed is model state the frontend cannot
     * see, and action update() cannot suspend, so the backend pushes it instead.
     *
     * <p>A [kotlinx.coroutines.flow.StateFlow] on the backend: a new subscriber immediately receives
     * the current set, and every rename, reset, reorder or tab load republishes it.
     */
    suspend fun getCustomNamedTabs(projectId: ProjectId): Flow<List<Int>>

    companion object {
        suspend fun getInstance(): UtilRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<UtilRpcApi>())
        }
    }
}
