package com.sabifox.vpn.platform.windows

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.platform.AppPaths
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

object WindowsShell {
    private const val TAG = "WindowsShell"
    private const val SW_SHOWNORMAL = 1

    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** True when SABI-FOX runs with administrator rights (needed for the system-wide tunnel). */
    val isElevated: Boolean by lazy {
        if (!isWindows) false else runCatching { Shell32Ex.INSTANCE.IsUserAnAdmin() }.getOrDefault(false)
    }

    /**
     * Starts SABI-FOX again with administrator rights (Windows shows its UAC prompt).
     * Returns false when the user declined or elevation is not possible.
     */
    fun relaunchElevated(arguments: List<String>): Boolean {
        val exe = AppPaths.launcherExe ?: return false
        return runCatching {
                val params = arguments.joinToString(" ") { quote(it) }
                val result =
                    Shell32Ex.INSTANCE.ShellExecuteW(
                        null,
                        WString("runas"),
                        WString(exe.absolutePath),
                        WString(params),
                        WString(exe.parentFile.absolutePath),
                        SW_SHOWNORMAL,
                    )
                Pointer.nativeValue(result) > 32
            }
            .onFailure { AppLogger.w(TAG, "could not request elevation", it) }
            .getOrDefault(false)
    }

    fun quote(arg: String): String =
        if (arg.isNotEmpty() && arg.none { it == ' ' || it == '\t' || it == '"' }) arg
        else "\"" + arg.replace("\"", "\\\"") + "\""

    fun openUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" && uri.scheme != "http") return false
        return runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri)
                } else {
                    ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start()
                }
                true
            }
            .getOrDefault(false)
    }

    fun openFolder(folder: File): Boolean =
        runCatching {
                ProcessBuilder("explorer.exe", folder.absolutePath).start()
                true
            }
            .getOrDefault(false)

    fun revealFile(file: File): Boolean =
        runCatching {
                ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                true
            }
            .getOrDefault(false)

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
