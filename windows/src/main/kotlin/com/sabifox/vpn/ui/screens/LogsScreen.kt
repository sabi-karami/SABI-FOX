package com.sabifox.vpn.ui.screens

import com.sabifox.vpn.platform.Toast
import com.sabifox.vpn.platform.getString
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sabifox.vpn.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.LogEntry
import com.sabifox.vpn.data.LogLevel
import com.sabifox.vpn.ui.LogFilter
import com.sabifox.vpn.ui.LogsUserMessage
import com.sabifox.vpn.ui.LogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun LogFilter.label(): String =
    when (this) {
        LogFilter.ALL -> stringResource(Res.string.logs_filter_all)
        LogFilter.INFO -> stringResource(Res.string.logs_filter_info)
        LogFilter.PROBLEMS -> stringResource(Res.string.logs_filter_problems)
    }

@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = viewModel(),
) {
    val entries by AppLogger.entries.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val chooserTitle = stringResource(Res.string.logs_share_chooser_title)

    LaunchedEffect(state.shareFileIntent) {
        val file = state.shareFileIntent ?: return@LaunchedEffect
        com.sabifox.vpn.platform.windows.WindowsShell.revealFile(file)
        viewModel.consumeShareIntent()
    }

    LaunchedEffect(state.userMessage) {
        val msg = state.userMessage ?: return@LaunchedEffect
        val text =
            when (msg) {
                is LogsUserMessage.StringRes -> context.getString(msg.resId)
                is LogsUserMessage.PlainText -> msg.text
            }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
    }

    val countsByFilter: Map<LogFilter, Int> =
        remember(entries) {
            LogFilter.entries.associateWith { option ->
                entries.count { it.level in option.levels }
            }
        }
    val visible: List<LogEntry> =
        remember(entries, state.filter) {
            if (state.filter == LogFilter.ALL) {
                entries
            } else {
                entries.filter { it.level in state.filter.levels }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (visible.size == entries.size) {
                            stringResource(Res.string.logs_title_all, entries.size)
                        } else {
                            stringResource(Res.string.logs_title_filtered, visible.size, entries.size)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.isExporting && entries.isNotEmpty(),
                        onClick = { viewModel.exportLogs(context, chooserTitle) },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(Res.string.logs_action_share),
                        )
                    }
                    IconButton(
                        enabled = !state.isExporting && entries.isNotEmpty(),
                        onClick = { viewModel.copyLogs(context) },
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(Res.string.logs_action_copy),
                        )
                    }
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = { viewModel.clearLogs() },
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(Res.string.logs_action_clear),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in LogFilter.entries) {
                    val count = countsByFilter[option] ?: 0
                    FilterChip(
                        selected = state.filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text("${option.label()} ($count)") },
                    )
                }
            }

            if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    val emptyText =
                        when {
                            entries.isEmpty() -> stringResource(Res.string.logs_empty_none)
                            state.filter == LogFilter.PROBLEMS ->
                                stringResource(Res.string.logs_empty_problems)
                            else -> stringResource(Res.string.logs_empty_filter)
                        }
                    Text(
                        text = emptyText,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible.asReversed(), key = { it.id }) { entry ->
                        val color =
                            when (entry.level) {
                                LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                                LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                            }
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "${timeFormat.format(Date(entry.timestampMillis))} ${entry.level.name} [${entry.tag}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
