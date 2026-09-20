package rpc

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class UtilCommandService {
    private val _commands = MutableSharedFlow<UtilCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    /**
     * The frontend's "Enable preview tab" setting, pushed over RPC. null = not yet reported.
     * Used by single-click handling to decide whether to open a file, because in split mode the
     * backend's own UISettings is not synced with the frontend.
     */
    private val previewTabEnabled = AtomicReference<Boolean?>(null)

    /**
     * Renamed tabs: tab index -> the branch-based name it would revert to. A StateFlow rather than a
     * command, so the frontend can read the current value at any time — including right after it
     * subscribes — and so republishing an unchanged map costs subscribers nothing.
     */
    private val _renamedTabs = MutableStateFlow<Map<Int, String>>(emptyMap())
    val renamedTabs = _renamedTabs.asStateFlow()

    fun setRenamedTabs(tabs: Map<Int, String>) {
        _renamedTabs.value = tabs
    }

    fun selectInProject(filePath: String) {
        _commands.tryEmit(UtilCommand.SelectInProject(filePath))
    }

    /**
     * Asks the frontend to launch a file the editor cannot display in its associated application.
     * Deliberately not done here — see [UtilCommand.OpenInAssociatedApplication].
     */
    fun openInAssociatedApplication(filePath: String, fileName: String) {
        _commands.tryEmit(UtilCommand.OpenInAssociatedApplication(filePath, fileName))
    }

    fun setPreviewTabEnabled(enabled: Boolean) {
        previewTabEnabled.set(enabled)
    }

    /** Returns the frontend-reported preview-tab setting, or null if it hasn't been reported yet. */
    fun getPreviewTabEnabled(): Boolean? = previewTabEnabled.get()
}
