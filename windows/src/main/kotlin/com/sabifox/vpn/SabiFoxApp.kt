package com.sabifox.vpn

import com.sabifox.vpn.data.CrashReporter
import com.sabifox.vpn.data.DesktopSettings
import com.sabifox.vpn.data.FxaAuthRepository
import com.sabifox.vpn.data.GuardianClient
import com.sabifox.vpn.data.ProxyStateStore
import com.sabifox.vpn.data.ServerListClient
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.TokenStore
import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.update.AppUpdater

interface AppContainer {
    val tokenStore: TokenStore
    val proxyStateStore: ProxyStateStore
    val settingsStore: SettingsStore
    val guardianClient: GuardianClient
    val serverListClient: ServerListClient
    val authRepository: FxaAuthRepository
}

/** The desktop application object: same container the Android Application exposes. */
class SabiFoxApp private constructor() : Context(), AppContainer {
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
    lateinit var desktopSettings: DesktopSettings
        internal set

    private fun onCreate() {
        CrashReporter.install(this)
        CrashReporter.replayLastCrashIfAny(this)
        desktopSettings = DesktopSettings(this)
        appUpdater = AppUpdater(this)
        tokenStore = TokenStore(this)
        proxyStateStore = ProxyStateStore(this)
        settingsStore = SettingsStore(this)
        guardianClient = GuardianClient()
        serverListClient = ServerListClient(this)
        authRepository = FxaAuthRepository(tokenStore)
    }

    companion object {
        @Volatile private var instance: SabiFoxApp? = null

        fun get(): SabiFoxApp = instance ?: error("SABI-FOX is not initialised")

        @Synchronized
        fun create(): SabiFoxApp = instance ?: SabiFoxApp().also {
            instance = it
            it.onCreate()
        }
    }
}
