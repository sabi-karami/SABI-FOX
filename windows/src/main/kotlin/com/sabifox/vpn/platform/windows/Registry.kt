package com.sabifox.vpn.platform.windows

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

internal object Registry {
    private const val APP_PATHS = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths"
    private const val RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val LAYERS = "Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers"
    private const val INTERNET = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

    /** Executables registered under App Paths (HKLM and HKCU). */
    fun appPaths(): List<String> {
        val result = mutableListOf<String>()
        for (root in listOf(WinReg.HKEY_LOCAL_MACHINE, WinReg.HKEY_CURRENT_USER)) {
            val keys = runCatching { Advapi32Util.registryGetKeys(root, APP_PATHS) }.getOrDefault(emptyArray())
            for (key in keys) {
                val value =
                    runCatching { Advapi32Util.registryGetValues(root, "$APP_PATHS\\$key")[""] as? String }
                        .getOrNull()
                if (!value.isNullOrBlank()) result.add(expand(value))
            }
        }
        return result
    }

    private fun expand(value: String): String =
        Regex("%([^%]+)%").replace(value) { System.getenv(it.groupValues[1]) ?: it.value }

    fun setRunAtLogon(name: String, command: String?) {
        if (command == null) {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, name))
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN, name)
        } else {
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN, name, command)
        }
    }

    fun runAtLogon(name: String): String? =
        runCatching {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, name))
                    Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN, name)
                else null
            }
            .getOrNull()

    fun setRunAsAdmin(exe: String, enabled: Boolean) {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, LAYERS))
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, LAYERS)
        if (enabled) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, LAYERS, exe, "~ RUNASADMIN")
        else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, LAYERS, exe))
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, LAYERS, exe)
    }

    fun runAsAdmin(exe: String): Boolean =
        runCatching {
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, LAYERS, exe) &&
                    Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, LAYERS, exe)
                        .contains("RUNASADMIN", ignoreCase = true)
            }
            .getOrDefault(false)

    data class ProxyState(val enabled: Boolean, val server: String?)

    fun systemProxy(): ProxyState =
        runCatching {
                val enabled =
                    Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, INTERNET, "ProxyEnable") &&
                        Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET, "ProxyEnable") != 0
                val server =
                    if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, INTERNET, "ProxyServer"))
                        Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET, "ProxyServer")
                    else null
                ProxyState(enabled, server)
            }
            .getOrDefault(ProxyState(false, null))

    fun disableSystemProxy() {
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET, "ProxyEnable", 0)
        WinInet.notifySettingsChanged()
    }
}
