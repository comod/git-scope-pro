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

    companion object {
        suspend fun getInstance(): UtilRpcApi {
            return RemoteApiProviderService.resolve(remoteApiDescriptor<UtilRpcApi>())
        }
    }
}
