package com.sabifox.vpn.platform.windows

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.platform.AppPaths
import java.io.File

/**
 * The core engine sets and restores the Windows system proxy itself. If SABI-FOX is killed
 * abruptly the proxy could stay pointed at a closed port and break browsing, so a marker file
 * remembers what was set and the next launch repairs it.
 */
object SystemProxyGuard {
    private const val TAG = "SystemProxyGuard"
    private val marker = File(AppPaths.runtime, "system-proxy.active")

    fun armed(server: String) {
        runCatching { marker.writeText(server) }
    }

    fun disarmed() {
        runCatching { marker.delete() }
    }

    fun repairIfStale() {
        if (!marker.isFile) return
        val ours = runCatching { marker.readText().trim() }.getOrNull().orEmpty()
        val state = Registry.systemProxy()
        if (state.enabled && ours.isNotEmpty() && state.server?.contains(ours) == true) {
            runCatching { Registry.disableSystemProxy() }
                .onSuccess { AppLogger.w(TAG, "restored the Windows proxy left behind by an unclean exit") }
        }
        disarmed()
    }
}
