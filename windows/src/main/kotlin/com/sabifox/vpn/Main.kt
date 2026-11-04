package com.sabifox.vpn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import com.sabifox.vpn.data.AppLanguage
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.platform.LocalContext
import com.sabifox.vpn.platform.UiMessages
import com.sabifox.vpn.platform.windows.SingleInstance
import com.sabifox.vpn.platform.windows.SystemProxyGuard
import com.sabifox.vpn.platform.windows.WindowsShell
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import com.sabifox.vpn.ui.navigation.SabiNavGraph
import com.sabifox.vpn.ui.theme.SabiFoxTheme
import com.sabifox.vpn.ui.theme.rememberThemeController
import com.sabifox.vpn.update.UpdatePrompt
import com.sabifox.vpn.vpn.SabiFoxService
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TAG = "Main"
private const val ARG_MINIMIZED = "--minimized"
private const val ARG_CONNECT = "--connect"
private const val ARG_HANDOFF = "--handoff"

fun main(args: Array<String>) {
    val handoff = ARG_HANDOFF in args
    // A second launch hands its request to the running window and exits.
    if (!SingleInstance.tryAcquire(if (handoff) 15_000L else 0L)) {
        val command = if (ARG_CONNECT in args) "connect" else "show"
        SingleInstance.signalRunningInstance(command)
        exitProcess(0)
    }

    val app = SabiFoxApp.create()
    Locale.setDefault(app.desktopSettings.language.resolve())
    runCatching { SystemProxyGuard.repairIfStale() }
    SabiFoxService.attach(app)
    AppLogger.i(TAG, "SABI-FOX ${BuildConfig.VERSION_NAME} for Windows started" + if (WindowsShell.isElevated) " (administrator)" else "")

    val startHidden = ARG_MINIMIZED in args && app.desktopSettings.startMinimized
    val connectRequested = ARG_CONNECT in args || app.desktopSettings.connectOnLaunch || app.appUpdater.consumeReconnectAfterUpdate()

    application(exitProcessOnExit = true) {
        var windowVisible by remember { mutableStateOf(!startHidden) }
        var elevationPrompt by remember { mutableStateOf(false) }
        var quitting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val state by SabiFoxService.state.collectAsState()
        val language = app.desktopSettings.language
        val windowHeight = remember { initialWindowHeight() }
        val windowState = rememberWindowState(size = DpSize(420.dp, windowHeight.dp), position = WindowPosition(Alignment.Center))

        fun quit() {
            if (quitting) return
            quitting = true
            windowVisible = false
            scope.launch {
                runCatching { SabiFoxService.shutdown() }
                SingleInstance.release()
                exitApplication()
            }
        }

        fun requestConnect() {
            if (!app.settingsStore.proxyOnlyMode && !WindowsShell.isElevated) {
                windowVisible = true
                elevationPrompt = true
                return
            }
            SabiFoxService.start(app)
        }

        fun restartElevated() {
            elevationPrompt = false
            SingleInstance.release()
            if (WindowsShell.relaunchElevated(listOf(ARG_HANDOFF, ARG_CONNECT))) {
                quit()
            } else {
                // The user declined the Windows prompt; keep running normally.
                SingleInstance.tryAcquire(2_000L)
                SingleInstance.listen { }
            }
        }

        LaunchedEffect(Unit) {
            app.appUpdater.exitForUpdate = { quit() }
            SingleInstance.listen { command ->
                scope.launch {
                    when (command) {
                        "show" -> {
                            windowVisible = true
                            windowState.isMinimized = false
                        }
                        "connect" -> {
                            windowVisible = true
                            requestConnect()
                        }
                        "disconnect" -> SabiFoxService.stop(app)
                    }
                }
            }
            if (connectRequested && app.tokenStore.hasStoredSession()) {
                delay(1_500L)
                requestConnect()
            }
            delay(5_000L)
            val updater = app.appUpdater
            if (updater.autoInstall) updater.launchAutomaticUpdate() else updater.check(automatic = true)
            updater.startPeriodicChecks()
        }

        // CI only: flips the language at runtime to prove the live switch keeps working.
        if (System.getProperty("sabifox.selftest.languageSwitch") != null) {
            LaunchedEffect(Unit) {
                delay(8_000L)
                app.desktopSettings.updateLanguage(if (language.isRtl()) AppLanguage.ENGLISH else AppLanguage.PERSIAN)
            }
        }

        val connected = state == ConnectionState.CONNECTED
        val trayState = rememberTrayState()
        key(language) {
        Tray(
            icon = painterResource(Res.drawable.app_icon),
            state = trayState,
            tooltip =
                "SABI-FOX — " +
                    when (state) {
                        ConnectionState.CONNECTED -> stringResource(Res.string.desktop_tray_connected)
                        ConnectionState.DISCONNECTED -> stringResource(Res.string.desktop_tray_disconnected)
                        else -> stringResource(Res.string.desktop_tray_connecting)
                    },
            onAction = {
                windowVisible = true
                windowState.isMinimized = false
            },
            menu = {
                Item(stringResource(Res.string.desktop_tray_open), onClick = {
                    windowVisible = true
                    windowState.isMinimized = false
                })
                if (state == ConnectionState.DISCONNECTED) {
                    Item(stringResource(Res.string.desktop_tray_connect), enabled = app.tokenStore.hasStoredSession(), onClick = { requestConnect() })
                } else {
                    Item(stringResource(Res.string.desktop_tray_disconnect), onClick = { SabiFoxService.stop(app) })
                }
                Separator()
                Item(stringResource(Res.string.desktop_tray_quit), onClick = { quit() })
            },
        )
        }

        Window(
            onCloseRequest = { if (app.desktopSettings.closeToTray) windowVisible = false else quit() },
            visible = windowVisible,
            state = windowState,
            title = "SABI-FOX" + if (connected) " • " + stringResource(Res.string.desktop_tray_connected) else "",
            icon = painterResource(Res.drawable.app_icon),
        ) {
            window.minimumSize = java.awt.Dimension(380, minOf(640, windowHeight))
            // The navigation state and its ViewModelStore outlive a language switch: NavHost
            // rejects a new store once its graph is set, so only the UI below is rebuilt.
            val navController = rememberNavController()
            val viewModelOwner = remember {
                object : androidx.lifecycle.ViewModelStoreOwner {
                    override val viewModelStore = androidx.lifecycle.ViewModelStore()
                }
            }
            CompositionLocalProvider(
                LocalContext provides app,
                androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides viewModelOwner,
            ) {
                key(language) {
                    val themeController = rememberThemeController(app.settingsStore)
                    SabiFoxTheme(themeMode = themeController.mode, rtl = language.isRtl()) {
                        val snackbar = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) { UiMessages.messages.collect { snackbar.showSnackbar(it) } }
                        Surface(
                            modifier =
                                Modifier.fillMaxSize().onEscape {
                                    if (navController.previousBackStackEntry != null) navController.popBackStack() else false
                                }
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                UpdatePrompt(app.appUpdater)
                                SabiNavGraph(
                                    navController = navController,
                                    container = app,
                                    themeController = themeController,
                                    onRequestConnect = { requestConnect() },
                                    onDisconnect = { SabiFoxService.stop(app) },
                                )
                                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                            }
                            if (elevationPrompt) {
                                AlertDialog(
                                    onDismissRequest = { elevationPrompt = false },
                                    title = { Text(stringResource(Res.string.desktop_admin_title)) },
                                    text = { Text(stringResource(Res.string.desktop_admin_message)) },
                                    confirmButton = {
                                        TextButton(onClick = { restartElevated() }) {
                                            Text(stringResource(Res.string.desktop_admin_restart))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            elevationPrompt = false
                                            app.settingsStore.proxyOnlyMode = true
                                            SabiFoxService.start(app)
                                        }) {
                                            Text(stringResource(Res.string.desktop_admin_use_proxy))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 840 dp tall, but never taller than the work area (many laptops are only 768 px high). */
private fun initialWindowHeight(): Int {
    val available =
        runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height }
            .getOrDefault(900)
    return minOf(840, available - 40).coerceAtLeast(480)
}

/** Esc goes back one screen, like the Android back gesture. */
private fun Modifier.onEscape(action: () -> Boolean): Modifier =
    onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == Key.Escape && action() }
