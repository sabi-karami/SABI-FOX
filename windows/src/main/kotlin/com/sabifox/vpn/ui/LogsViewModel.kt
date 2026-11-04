package com.sabifox.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.LogLevel
import com.sabifox.vpn.platform.AppPaths
import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.windows.WindowsShell
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

enum class LogFilter(val levels: Set<LogLevel>) {
    ALL(setOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)),
    INFO(setOf(LogLevel.INFO)),
    PROBLEMS(setOf(LogLevel.WARN, LogLevel.ERROR)),
}

sealed interface LogsUserMessage {
    data class StringRes(val resId: StringResource) : LogsUserMessage
    data class PlainText(val text: String) : LogsUserMessage
}

data class LogsUiState(
    val filter: LogFilter = LogFilter.ALL,
    val isExporting: Boolean = false,
    val userMessage: LogsUserMessage? = null,
    /** On Windows the exported file is revealed in File Explorer instead of a share sheet. */
    val shareFileIntent: File? = null,
)

class LogsViewModel : ViewModel() {

    private val _state = MutableStateFlow(LogsUiState())
    val state: StateFlow<LogsUiState> = _state.asStateFlow()

    fun setFilter(filter: LogFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun clearLogs() {
        AppLogger.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    fun exportLogs(context: Context, chooserTitle: String) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            try {
                val file = writeLogFile()
                _state.update { it.copy(shareFileIntent = file, isExporting = false) }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isExporting = false) }
                throw cancelled
            } catch (error: Exception) {
                AppLogger.w("LogsViewModel", "Could not export logs", error)
                _state.update {
                    it.copy(isExporting = false, userMessage = LogsUserMessage.StringRes(Res.string.logs_share_failed))
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun copyLogs(context: Context) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { AppLogger.exportAsText() }
                WindowsShell.copyToClipboard(text)
                _state.update { it.copy(isExporting = false, userMessage = LogsUserMessage.StringRes(Res.string.logs_copied)) }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isExporting = false) }
                throw cancelled
            } catch (error: Exception) {
                AppLogger.w("LogsViewModel", "Could not copy logs", error)
                _state.update {
                    it.copy(isExporting = false, userMessage = LogsUserMessage.StringRes(Res.string.logs_copy_failed))
                }
            }
        }
    }

    fun consumeShareIntent() {
        _state.update { it.copy(shareFileIntent = null) }
    }

    fun consumeUserMessage() {
        _state.update { it.copy(userMessage = null) }
    }

    companion object {
        private val exportMutex = Mutex()

        private suspend fun writeLogFile(): File =
            withContext(Dispatchers.IO) {
                exportMutex.withLock {
                    val dir = AppPaths.logs
                    val now = System.currentTimeMillis()
                    dir.listFiles()
                        ?.filter { it.isFile && it.name.startsWith("sabifox-logs-") && it.extension == "txt" }
                        ?.sortedByDescending { it.lastModified() }
                        ?.forEachIndexed { index, old ->
                            if (index >= 9 || now - old.lastModified() >= 7 * 24 * 60 * 60_000L) old.delete()
                        }
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(now))
                    val file = File(dir, "sabifox-logs-$stamp.txt")
                    file.writeText(AppLogger.exportAsText())
                    file
                }
            }
    }
}
