package rpc

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Service(Service.Level.PROJECT)
class UtilCommandService {
    private val _commands = MutableSharedFlow<UtilCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    fun selectInProject(filePath: String) {
        _commands.tryEmit(UtilCommand.SelectInProject(filePath))
    }

    @JvmOverloads
    fun openPreviewTab(filePath: String, line: Int = -1) {
        _commands.tryEmit(UtilCommand.OpenPreviewTab(filePath, line))
    }
}
