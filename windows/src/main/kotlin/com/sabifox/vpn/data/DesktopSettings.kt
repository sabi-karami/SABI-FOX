package com.sabifox.vpn.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sabifox.vpn.platform.AppPaths
import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.windows.Registry
import com.sabifox.vpn.platform.windows.Startup
import com.sabifox.vpn.platform.windows.WindowsShell
import java.util.Locale

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    PERSIAN("fa");

    /** The locale the interface uses for this choice. */
    fun resolve(): Locale = tag?.let { Locale.forLanguageTag(it) } ?: AppLanguageDefaults.systemLocale

    fun isRtl(): Boolean = resolve().language in setOf("fa", "ar", "ur", "he", "iw", "ps", "ckb")
}

object AppLanguageDefaults {
    /** The Windows display language, captured before SABI-FOX overrides the JVM default. */
    val systemLocale: Locale = Locale.getDefault()
}

/**
 * Windows-only preferences. Values are observable from Compose so toggles update instantly.
 */
class DesktopSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sabifox_desktop", Context.MODE_PRIVATE)

    var language: AppLanguage by mutableStateOf(
        runCatching { AppLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "") }
            .getOrDefault(defaultLanguage())
    )
        private set

    fun updateLanguage(value: AppLanguage) {
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.name).apply()
        Locale.setDefault(value.resolve())
    }

    /** In proxy-only mode, point the Windows system proxy at SABI-FOX while connected. */
    var systemProxy: Boolean by mutableStateOf(prefs.getBoolean(KEY_SYSTEM_PROXY, true))
        private set

    fun updateSystemProxy(value: Boolean) {
        systemProxy = value
        prefs.edit().putBoolean(KEY_SYSTEM_PROXY, value).apply()
    }

    var closeToTray: Boolean by mutableStateOf(prefs.getBoolean(KEY_CLOSE_TO_TRAY, true))
        private set

    fun updateCloseToTray(value: Boolean) {
        closeToTray = value
        prefs.edit().putBoolean(KEY_CLOSE_TO_TRAY, value).apply()
    }

    var connectOnLaunch: Boolean by mutableStateOf(prefs.getBoolean(KEY_CONNECT_ON_LAUNCH, false))
        private set

    fun updateConnectOnLaunch(value: Boolean) {
        connectOnLaunch = value
        prefs.edit().putBoolean(KEY_CONNECT_ON_LAUNCH, value).apply()
    }

    var startMinimized: Boolean by mutableStateOf(prefs.getBoolean(KEY_START_MINIMIZED, true))
        private set

    fun updateStartMinimized(value: Boolean) {
        startMinimized = value
        prefs.edit().putBoolean(KEY_START_MINIMIZED, value).apply()
    }

    /** True for the installed and portable app; false when running from a developer build. */
    val canManageStartup: Boolean
        get() = AppPaths.launcherExe != null

    var launchAtStartup: Boolean by mutableStateOf(canManageStartup && runCatching { Startup.isEnabled() }.getOrDefault(false))
        private set

    fun updateLaunchAtStartup(value: Boolean): Result<Unit> =
        Startup.apply(value, alwaysRunAsAdmin).onSuccess { launchAtStartup = value }

    var alwaysRunAsAdmin: Boolean by mutableStateOf(
        AppPaths.launcherExe?.let { exe -> runCatching { Registry.runAsAdmin(exe.absolutePath) }.getOrDefault(false) } == true
    )
        private set

    fun updateAlwaysRunAsAdmin(value: Boolean): Result<Unit> = runCatching {
        val exe = AppPaths.launcherExe ?: error("Only available in the installed or portable app")
        Registry.setRunAsAdmin(exe.absolutePath, value)
        alwaysRunAsAdmin = value
        if (launchAtStartup) {
            // A Run-key entry cannot start an elevated app; move startup to a logon task.
            Startup.apply(true, value).getOrThrow()
        }
    }

    val isElevated: Boolean
        get() = WindowsShell.isElevated

    private fun defaultLanguage(): AppLanguage =
        if (AppLanguageDefaults.systemLocale.language == "fa") AppLanguage.PERSIAN else AppLanguage.SYSTEM

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_SYSTEM_PROXY = "system_proxy"
        const val KEY_CLOSE_TO_TRAY = "close_to_tray"
        const val KEY_CONNECT_ON_LAUNCH = "connect_on_launch"
        const val KEY_START_MINIMIZED = "start_minimized"
    }
}
