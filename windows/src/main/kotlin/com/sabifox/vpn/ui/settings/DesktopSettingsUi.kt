package com.sabifox.vpn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.sabifox.vpn.SabiFoxApp
import com.sabifox.vpn.data.AppLanguage
import com.sabifox.vpn.platform.Toast
import com.sabifox.vpn.platform.windows.WindowsShell
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.jetbrains.compose.resources.stringResource

/** Opens the Windows file picker for an .exe to add to split tunneling. */
internal fun pickProgram(): String? {
    val dialog = FileDialog(null as Frame?, "SABI-FOX", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".exe", ignoreCase = true) }
    dialog.file = "*.exe"
    System.getenv("ProgramFiles")?.let { dialog.directory = it }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, file).takeIf { it.isFile && it.name.endsWith(".exe", true) }?.absolutePath
}

@Composable
private fun Toggle(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) },
    )
}

/** Replaces Android's "Always-on VPN" entry: the Windows system proxy and admin status. */
@Composable
internal fun DesktopConnectionSettings(proxyOnlyMode: Boolean) {
    val desktop = SabiFoxApp.get().desktopSettings
    Toggle(
        title = stringResource(Res.string.desktop_system_proxy),
        summary = stringResource(Res.string.desktop_system_proxy_help),
        checked = desktop.systemProxy,
        enabled = proxyOnlyMode,
        onChange = desktop::updateSystemProxy,
    )
    if (!proxyOnlyMode && !WindowsShell.isElevated) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.desktop_admin_title)) },
            supportingContent = { Text(stringResource(Res.string.desktop_admin_settings_help)) },
            leadingContent = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable {
                com.sabifox.vpn.platform.windows.SingleInstance.release()
                if (WindowsShell.relaunchElevated(listOf("--handoff"))) {
                    Runtime.getRuntime().halt(0)
                } else {
                    com.sabifox.vpn.platform.windows.SingleInstance.tryAcquire(2_000L)
                }
            },
        )
    }
}

/** Windows app behaviour: language, startup, tray and administrator mode. */
@Composable
internal fun DesktopAppSettings() {
    val app = SabiFoxApp.get()
    val desktop = app.desktopSettings
    var languageDialog by remember { mutableStateOf(false) }
    val failed = stringResource(Res.string.desktop_setting_failed)

    Text(
        stringResource(Res.string.desktop_section_windows),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ListItem(
        headlineContent = { Text(stringResource(Res.string.desktop_language)) },
        supportingContent = { Text(languageLabel(desktop.language)) },
        leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
        modifier = Modifier.clickable { languageDialog = true },
    )
    Toggle(
        title = stringResource(Res.string.desktop_launch_at_startup),
        summary = stringResource(Res.string.desktop_launch_at_startup_help),
        checked = desktop.launchAtStartup,
        enabled = desktop.canManageStartup,
    ) { value ->
        desktop.updateLaunchAtStartup(value).onFailure { Toast.makeText(app, it.message ?: failed, Toast.LENGTH_LONG).show() }
    }
    Toggle(
        title = stringResource(Res.string.desktop_start_minimized),
        summary = stringResource(Res.string.desktop_start_minimized_help),
        checked = desktop.startMinimized,
        enabled = desktop.launchAtStartup,
        onChange = desktop::updateStartMinimized,
    )
    Toggle(
        title = stringResource(Res.string.desktop_connect_on_launch),
        summary = stringResource(Res.string.desktop_connect_on_launch_help),
        checked = desktop.connectOnLaunch,
        onChange = desktop::updateConnectOnLaunch,
    )
    Toggle(
        title = stringResource(Res.string.desktop_close_to_tray),
        summary = stringResource(Res.string.desktop_close_to_tray_help),
        checked = desktop.closeToTray,
        onChange = desktop::updateCloseToTray,
    )
    Toggle(
        title = stringResource(Res.string.desktop_always_admin),
        summary = stringResource(Res.string.desktop_always_admin_help),
        checked = desktop.alwaysRunAsAdmin,
        enabled = desktop.canManageStartup,
    ) { value ->
        desktop.updateAlwaysRunAsAdmin(value).onFailure { Toast.makeText(app, it.message ?: failed, Toast.LENGTH_LONG).show() }
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(Res.string.desktop_language)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = desktop.language == option,
                                role = Role.RadioButton,
                                onClick = {
                                    languageDialog = false
                                    desktop.updateLanguage(option)
                                },
                            ),
                        ) {
                            RadioButton(selected = desktop.language == option, onClick = null)
                            Text(languageLabel(option), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(Res.string.close)) } },
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SYSTEM -> stringResource(Res.string.desktop_language_system)
        AppLanguage.ENGLISH -> "English"
        AppLanguage.PERSIAN -> "فارسی"
    }
