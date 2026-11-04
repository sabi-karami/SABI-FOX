package com.sabifox.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import com.sabifox.vpn.data.CrashReporter
import com.sabifox.vpn.data.FxaAuthRepository
import com.sabifox.vpn.data.GuardianClient
import com.sabifox.vpn.data.ProxyStateStore
import com.sabifox.vpn.data.ServerListClient
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.TokenStore
import com.sabifox.vpn.update.AppUpdater
import com.sabifox.vpn.vpn.SabiFoxService
import com.sabifox.vpn.widget.QuickConnectWidgetProvider

interface AppContainer {
    val tokenStore: TokenStore
    val proxyStateStore: ProxyStateStore
    val settingsStore: SettingsStore
    val guardianClient: GuardianClient
    val serverListClient: ServerListClient
    val authRepository: FxaAuthRepository
}

class SabiFoxApp : Application(), AppContainer {

    internal lateinit var appUpdater: AppUpdater
    override lateinit var tokenStore: TokenStore
        internal set
    override lateinit var proxyStateStore: ProxyStateStore
        internal set
    override lateinit var settingsStore: SettingsStore
        internal set
    override lateinit var guardianClient: GuardianClient
        internal set
    override lateinit var serverListClient: ServerListClient
        internal set
    override lateinit var authRepository: FxaAuthRepository
        internal set

    override fun onCreate() {
        super.onCreate()

        CrashReporter.install(this)
        CrashReporter.replayLastCrashIfAny(this)

        appUpdater = AppUpdater(this)
        tokenStore = TokenStore(this)
        proxyStateStore = ProxyStateStore(this)
        settingsStore = SettingsStore(this)
        guardianClient = GuardianClient()
        serverListClient = ServerListClient(this)
        authRepository = FxaAuthRepository(tokenStore)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                VPN_NOTIFICATION_CHANNEL_ID,
                "VPN status",
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        QuickConnectWidgetProvider.updateAll(this, SabiFoxService.state.value)
    }

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "sabifox_status"
    }
}
