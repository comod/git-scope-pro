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
     * Indices of tabs carrying a custom name. A StateFlow rather than a command, so the frontend can
     * read the current value at any time — including right after it subscribes — to decide whether
     * "Reset Tab Name" has anything to reset.
     */
    private val _customNamedTabs = MutableStateFlow<List<Int>>(emptyList())
    val customNamedTabs = _customNamedTabs.asStateFlow()

    fun setCustomNamedTabs(indices: List<Int>) {
        _customNamedTabs.value = indices
    }

    fun selectInProject(filePath: String) {
        _commands.tryEmit(UtilCommand.SelectInProject(filePath))
    }

    fun setPreviewTabEnabled(enabled: Boolean) {
        previewTabEnabled.set(enabled)
    }

    /** Returns the frontend-reported preview-tab setting, or null if it hasn't been reported yet. */
    fun getPreviewTabEnabled(): Boolean? = previewTabEnabled.get()
}
