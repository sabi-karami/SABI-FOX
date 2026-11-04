package com.sabifox.vpn.platform.windows

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.platform.AppPaths
import java.util.concurrent.TimeUnit

/**
 * Starting SABI-FOX when the user signs in to Windows. A normal start uses the per-user Run
 * key; when SABI-FOX is set to always run as administrator a logon task with highest
 * privileges is used instead, because Windows silently skips elevated Run entries.
 */
object Startup {
    private const val TAG = "Startup"
    private const val NAME = "SABI-FOX"

    private fun command(): String? =
        AppPaths.launcherExe?.let { "${WindowsShell.quote(it.absolutePath)} --minimized" }

    fun isEnabled(): Boolean = Registry.runAtLogon(NAME) != null || taskExists()

    fun apply(enabled: Boolean, elevated: Boolean): Result<Unit> = runCatching {
        val cmd = command() ?: error("Startup is only available in the installed or portable app")
        Registry.setRunAtLogon(NAME, null)
        deleteTask()
        if (!enabled) return@runCatching
        if (elevated) {
            check(WindowsShell.isElevated) { "Restart SABI-FOX as administrator to change this setting" }
            val exit =
                run("schtasks", "/Create", "/TN", NAME, "/TR", cmd, "/SC", "ONLOGON", "/RL", "HIGHEST", "/F")
            check(exit == 0) { "Windows did not accept the startup task" }
        } else {
            Registry.setRunAtLogon(NAME, cmd)
        }
    }

    private fun taskExists(): Boolean = run("schtasks", "/Query", "/TN", NAME) == 0

    private fun deleteTask() {
        if (taskExists()) run("schtasks", "/Delete", "/TN", NAME, "/F")
    }

    private fun run(vararg args: String): Int =
        runCatching {
                val process = ProcessBuilder(*args).redirectErrorStream(true).start()
                process.inputStream.readBytes()
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    -1
                } else process.exitValue()
            }
            .onFailure { AppLogger.w(TAG, "${args.firstOrNull()} failed", it) }
            .getOrDefault(-1)
}
