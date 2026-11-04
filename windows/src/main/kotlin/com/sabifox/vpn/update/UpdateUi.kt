package com.sabifox.vpn.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sabifox.vpn.platform.LocalContext
import com.sabifox.vpn.platform.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdatePrompt(updater: AppUpdater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val visible by updater.promptVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installError by remember(state) { mutableStateOf<String?>(null) }
    var installing by remember(state) { mutableStateOf(false) }
    if (!visible) return
    AlertDialog(
        onDismissRequest = { updater.hidePrompt() },
        title = { Text(stringResource(Res.string.update_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    when (val current = state) {
                        UpdateState.Idle -> stringResource(Res.string.update_idle_help)
                        UpdateState.Checking -> stringResource(Res.string.update_checking)
                        UpdateState.Current -> stringResource(Res.string.update_current)
                        is UpdateState.Available ->
                            stringResource(Res.string.update_available_msg, current.release.tag)
                        is UpdateState.Downloading ->
                            stringResource(
                                Res.string.update_downloading_fmt,
                                current.bytes / (1024 * 1024),
                                current.total / (1024 * 1024),
                            )
                        is UpdateState.Ready ->
                            stringResource(Res.string.update_ready_msg, current.version)
                        is UpdateState.Failed -> current.message
                    }
                )
                if (state is UpdateState.Downloading || state == UpdateState.Checking) {
                    Spacer(Modifier.height(12.dp))
                    val progress = state as? UpdateState.Downloading
                    if (progress == null) LinearProgressIndicator()
                    else
                        LinearProgressIndicator(
                            progress = {
                                progress.bytes.toFloat() / progress.total.coerceAtLeast(1)
                            }
                        )
                }
                installError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateState.Available ->
                    TextButton(onClick = { updater.download(current.release) }) {
                        Text(stringResource(Res.string.update_btn_download))
                    }
                is UpdateState.Ready ->
                    TextButton(
                        enabled = !installing,
                        onClick = {
                            installing = true
                            scope.launch {
                                try {
                                    if (updater.canInstallPackages()) {
                                        updater.installVerified(current)
                                    } else {
                                        updater.openReleasePage()
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    installError =
                                        error.message
                                            ?: context.getString(Res.string.update_installer_failed)
                                } finally {
                                    installing = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.update_btn_install))
                    }
                UpdateState.Checking,
                is UpdateState.Downloading ->
                    TextButton(onClick = { updater.cancel() }) {
                        Text(stringResource(Res.string.cancel))
                    }
                else ->
                    TextButton(onClick = { updater.check() }) {
                        Text(
                            if (state is UpdateState.Failed) {
                                stringResource(Res.string.retry)
                            } else {
                                stringResource(Res.string.update_btn_check)
                            }
                        )
                    }
            }
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.End) {
                if (state is UpdateState.Ready) {
                    TextButton(
                        enabled = !installing,
                        onClick = {
                            updater.cancel()
                            updater.check()
                        },
                    ) {
                        Text(stringResource(Res.string.update_btn_check_again))
                    }
                }
                TextButton(onClick = { updater.hidePrompt() }) {
                    Text(stringResource(Res.string.close))
                }
            }
        },
    )
}
