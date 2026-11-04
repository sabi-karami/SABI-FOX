package com.sabifox.vpn.ui.navigation

import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sabifox.vpn.AppContainer
import com.sabifox.vpn.ui.HomeViewModel
import com.sabifox.vpn.ui.ServerListViewModel
import com.sabifox.vpn.ui.screens.AccountsScreen
import com.sabifox.vpn.ui.screens.HomeScreen
import com.sabifox.vpn.ui.screens.LoginScreen
import com.sabifox.vpn.ui.screens.LogsScreen
import com.sabifox.vpn.ui.screens.ServerListScreen
import com.sabifox.vpn.ui.screens.SettingsScreen
import com.sabifox.vpn.ui.screens.SplashScreen
import com.sabifox.vpn.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SabiRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    const val ACCOUNTS = "accounts"
}

@Composable
fun SabiNavGraph(
    navController: NavHostController = rememberNavController(),
    container: AppContainer,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val sessionScope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    NavHost(navController = navController, startDestination = SabiRoutes.SPLASH) {
        composable(SabiRoutes.SPLASH) {
            SplashScreen(
                authRepository = container.authRepository,
                onSignedIn = {
                    navController.navigate(SabiRoutes.HOME) {
                        popUpTo(SabiRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNeedsLogin = {
                    navController.navigate(SabiRoutes.LOGIN) {
                        popUpTo(SabiRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(SabiRoutes.LOGIN) {
            LoginScreen(
                authRepository = container.authRepository,
                suggestedEmail = if (container.tokenStore.activeSlot == 0) "info2@sahebkarami.ir" else "info3@sahebkarami.ir",
                onChooseAccount = { navController.navigate(SabiRoutes.ACCOUNTS) },
                onSignedIn = {
                    navController.navigate(SabiRoutes.HOME) {
                        popUpTo(SabiRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(SabiRoutes.HOME) {
            val homeModel: HomeViewModel =
                viewModel(
                    factory =
                        HomeViewModel.factory(
                            settingsStore = container.settingsStore,
                            tokenStore = container.tokenStore,
                            guardianClient = container.guardianClient,
                            authRepository = container.authRepository,
                            connectivity = context.getSystemService(ConnectivityManager::class.java),
                        )
                )
            HomeScreen(
                proxyStateStore = container.proxyStateStore,
                homeModel = homeModel,
                themeController = themeController,
                onRequestConnect = onRequestConnect,
                onDisconnect = onDisconnect,
                onOpenServers = { navController.navigate(SabiRoutes.SERVERS) },
                onOpenSettings = { navController.navigate(SabiRoutes.SETTINGS) },
            )
        }
        composable(SabiRoutes.SERVERS) {
            val serverListModel: ServerListViewModel =
                viewModel(
                    factory =
                        ServerListViewModel.factory(
                            serverListClient = container.serverListClient,
                            proxyStateStore = container.proxyStateStore,
                        )
                )
            ServerListScreen(
                viewModel = serverListModel,
                proxyStateStore = container.proxyStateStore,
                onServerSelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SabiRoutes.SETTINGS) {
            val app = context.applicationContext as? com.sabifox.vpn.SabiFoxApp
            val updater = app?.appUpdater
            var autoUpdates by remember { mutableStateOf(updater?.automaticChecks ?: false) }
            var autoInstall by remember { mutableStateOf(updater?.autoInstall ?: false) }
            SettingsScreen(
                settingsStore = container.settingsStore,
                onOpenLogs = { navController.navigate(SabiRoutes.LOGS) },
                onOpenAccounts = { navController.navigate(SabiRoutes.ACCOUNTS) },
                signOutInProgress = signingOut,
                onCheckForUpdates = {
                    updater?.showPrompt()
                    updater?.check()
                },
                automaticUpdates = autoUpdates,
                onAutomaticUpdatesChange = { enabled ->
                    updater?.automaticChecks = enabled
                    autoUpdates = enabled
                },
                autoInstallUpdates = autoInstall,
                onAutoInstallUpdatesChange = { enabled ->
                    updater?.autoInstall = enabled
                    autoInstall = enabled
                    if (enabled && updater?.canInstallPackages() == false) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    }
                },
                canInstallUpdates = { updater?.canInstallPackages() ?: true },
                onSignOut = signOut@{
                    if (signingOut) return@signOut
                    signingOut = true
                    val epoch = container.tokenStore.sessionEpoch
                    onDisconnect()
                    sessionScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(container.tokenStore.clearIfCurrent(epoch)) {
                                    "Session changed. Try signing out again."
                                }
                                com.sabifox.vpn.data.ControlPlaneHttp.cookieJar.clear()
                                container.settingsStore.clearAccountSession()
                                container.proxyStateStore.clear()
                            }
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            navController.navigate(SabiRoutes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                error.message ?: "Could not clear the session",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            signingOut = false
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SabiRoutes.ACCOUNTS) {
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            var accounts by remember { mutableStateOf(container.tokenStore.listAccounts()) }
            var active by remember { mutableStateOf(container.tokenStore.activeSlot) }
            AccountsScreen(
                accounts = accounts,
                activeSlot = active,
                busy = busy,
                message = message,
                onBack = { navController.popBackStack() },
                onIdentify = {
                    if (!busy) {
                        busy = true
                        message = null
                        sessionScope.launch {
                            try {
                                container.authRepository.identifyCurrentSession()
                                accounts = container.tokenStore.listAccounts()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = error.message ?: "Could not identify account."
                            } finally { busy = false }
                        }
                    }
                },
                onRefresh = {
                    if (!busy) {
                        busy = true
                        message = null
                        sessionScope.launch {
                            try {
                                val epoch = container.tokenStore.sessionEpoch
                                val access = container.authRepository.currentAccessToken() ?: error("Sign in again to refresh balance.")
                                val result = container.guardianClient.fetchUserInfo(com.sabifox.vpn.data.GUARDIAN_ENDPOINT_DEFAULT, access)
                                withContext(Dispatchers.IO) {
                                    container.tokenStore.withCurrentSession(epoch) {
                                        container.tokenStore.saveQuotaIfCurrent(epoch, result.quotaRemaining, result.maxBytes)
                                        container.settingsStore.saveAccountQuota(
                                            !result.limitedBandwidth && result.quotaRemaining == null && result.maxBytes == null,
                                            result.quotaRemaining,
                                            result.maxBytes,
                                        )
                                    }
                                }
                                accounts = container.tokenStore.listAccounts()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = error.message ?: "Could not refresh balance."
                            } finally { busy = false }
                        }
                    }
                },
                onChoose = { slot ->
                    if (!busy && com.sabifox.vpn.vpn.SabiFoxService.state.value == com.sabifox.vpn.data.model.ConnectionState.DISCONNECTED) {
                        busy = true
                        message = null
                        sessionScope.launch {
                            try {
                                // Invalidate previous requests before choosing the destination account.
                                withContext(Dispatchers.IO) {
                                    container.tokenStore.selectAccount(slot)
                                    container.settingsStore.clearAccountSession()
                                    com.sabifox.vpn.data.ControlPlaneHttp.cookieJar.clear()
                                }
                                // Wait until WebView cookies are cleared so the next login does not silently use the previous account.
                                kotlinx.coroutines.withTimeout(10_000L) {
                                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                                        android.webkit.CookieManager.getInstance().removeAllCookies {
                                            if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                                        }
                                    }
                                }
                                android.webkit.CookieManager.getInstance().flush()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                active = container.tokenStore.activeSlot
                                accounts = container.tokenStore.listAccounts()
                                val signedIn = accounts[active].signedIn
                                navController.navigate(if (signedIn) SabiRoutes.HOME else SabiRoutes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = error.message ?: "Could not switch accounts."
                            } finally { busy = false }
                        }
                    }
                },
            )
        }
        composable(SabiRoutes.LOGS) { LogsScreen(onBack = { navController.popBackStack() }) }
    }
}
