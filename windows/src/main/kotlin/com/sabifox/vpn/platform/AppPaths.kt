package com.sabifox.vpn.platform

import java.io.File

/**
 * Where SABI-FOX keeps its data on Windows.
 *
 * Installed builds use %LOCALAPPDATA%\SABI-FOX. The portable build ships a `portable.txt`
 * marker next to SABI-FOX.exe and keeps everything in a `data` folder beside the executable,
 * so it can run from a USB stick without leaving anything behind.
 */
object AppPaths {
    /** Full path of the running SABI-FOX.exe launcher, or null when running from Gradle. */
    val launcherExe: File? = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }

    val installDir: File? = launcherExe?.parentFile

    val portable: Boolean = installDir?.let { File(it, "portable.txt").isFile } == true

    val root: File =
        if (portable) File(installDir, "data")
        else
            File(
                System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(System.getProperty("user.home"), "AppData/Local").path,
                "SABI-FOX",
            )

    val files: File get() = dir("files")
    val cache: File get() = dir("cache")
    val prefs: File get() = dir("prefs")
    val logs: File get() = dir("logs")
    val runtime: File get() = dir("runtime")
    val webView: File get() = dir("webview")

    /** Folder that holds the bundled native helpers (core engine and sign-in window). */
    val resources: File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
            ?: System.getenv("SABIFOX_RESOURCES")?.let(::File)?.takeIf { it.isDirectory }

    fun resource(name: String): File? = resources?.let { File(it, name) }?.takeIf { it.isFile }

    private fun dir(name: String): File = File(root, name).apply { mkdirs() }
}
