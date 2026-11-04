package com.sabifox.vpn.platform.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

internal interface Shell32Ex : StdCallLibrary {
    fun IsUserAnAdmin(): Boolean

    fun ShellExecuteW(
        hwnd: Pointer?,
        operation: WString?,
        file: WString,
        parameters: WString?,
        directory: WString?,
        showCmd: Int,
    ): Pointer?

    companion object {
        val INSTANCE: Shell32Ex by lazy { Native.load("shell32", Shell32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

internal interface WinInetLib : StdCallLibrary {
    fun InternetSetOptionW(handle: Pointer?, option: Int, buffer: Pointer?, length: Int): Boolean

    companion object {
        val INSTANCE: WinInetLib by lazy { Native.load("wininet", WinInetLib::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

internal object WinInet {
    private const val INTERNET_OPTION_SETTINGS_CHANGED = 39
    private const val INTERNET_OPTION_REFRESH = 37

    fun notifySettingsChanged() {
        runCatching {
            WinInetLib.INSTANCE.InternetSetOptionW(null, INTERNET_OPTION_SETTINGS_CHANGED, null, 0)
            WinInetLib.INSTANCE.InternetSetOptionW(null, INTERNET_OPTION_REFRESH, null, 0)
        }
    }
}
