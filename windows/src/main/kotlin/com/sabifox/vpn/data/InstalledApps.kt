package com.sabifox.vpn.data

import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.SystemClock
import com.sabifox.vpn.platform.windows.Registry
import java.io.File
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A program that can be included in or excluded from the tunnel. On Windows the "package name"
 * is the full path of the program's .exe file.
 */
internal data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isLaunchable: Boolean,
)

private val cacheMutex = Mutex()
@Volatile private var cachedApps: List<InstalledApp>? = null
@Volatile private var cachedAtElapsedMs = 0L
private const val CACHE_TTL_MS = 120_000L

/** Programs the user picked with "Add program…" that may not be running or registered. */
internal object CustomPrograms {
    private val extra = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun add(path: String) {
        extra.add(path)
        invalidateInstalledAppsCache()
    }

    fun all(): Set<String> = extra.toSet()
}

internal fun invalidateInstalledAppsCache() {
    cachedApps = null
    cachedAtElapsedMs = 0L
}

internal fun programLabel(path: String): String {
    val name = File(path).nameWithoutExtension
    return name.replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifBlank { File(path).name }
        .replaceFirstChar { it.uppercase() }
}

private fun isSystemPath(path: String): Boolean {
    val windows = (System.getenv("SystemRoot") ?: "C:\\Windows").lowercase()
    return path.lowercase().startsWith(windows)
}

internal suspend fun loadInstalledApps(context: Context, forceReload: Boolean = false): List<InstalledApp> =
    withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedApps
        if (!forceReload && cached != null && (now - cachedAtElapsedMs < CACHE_TTL_MS)) {
            return@withContext cached
        }
        cacheMutex.withLock {
            val ownExe = com.sabifox.vpn.platform.AppPaths.launcherExe?.absolutePath?.lowercase()
            val found = LinkedHashMap<String, InstalledApp>()
            fun add(path: String?, launchable: Boolean) {
                val clean = path?.trim()?.trim('"')?.takeIf { it.endsWith(".exe", ignoreCase = true) } ?: return
                val file = File(clean)
                if (!file.isFile) return
                val key = file.absolutePath.lowercase()
                if (key == ownExe) return
                val existing = found[key]
                if (existing != null) {
                    if (launchable && !existing.isLaunchable) found[key] = existing.copy(isLaunchable = true)
                    return
                }
                found[key] =
                    InstalledApp(
                        packageName = file.absolutePath,
                        label = programLabel(file.absolutePath),
                        isSystem = isSystemPath(file.absolutePath),
                        isLaunchable = launchable,
                    )
            }
            // Registered applications (App Paths) are what Windows itself treats as installed apps.
            runCatching { Registry.appPaths().forEach { add(it, launchable = true) } }
            // Anything currently running, so browsers, games and messengers show up immediately.
            runCatching {
                ProcessHandle.allProcesses().use { stream ->
                    stream.forEach { process -> add(process.info().command().orElse(null), launchable = false) }
                }
            }
            CustomPrograms.all().forEach { add(it, launchable = true) }
            runCatching {
                val settings = (context.applicationContext as? com.sabifox.vpn.AppContainer)?.settingsStore
                settings?.excludedApps?.forEach { add(it, launchable = true) }
                settings?.includedApps?.forEach { add(it, launchable = true) }
            }
            val collator = Collator.getInstance()
            val sorted =
                found.values.sortedWith { first, second ->
                    collator.compare(first.label, second.label).takeIf { it != 0 }
                        ?: first.packageName.compareTo(second.packageName)
                }
            cachedApps = sorted
            cachedAtElapsedMs = SystemClock.elapsedRealtime()
            sorted
        }
    }

internal fun filterInstalledApps(
    apps: List<InstalledApp>,
    query: String,
    showSystemApps: Boolean,
    selected: Set<String>,
): List<InstalledApp> {
    val search = query.trim()
    return apps.filter { app ->
        (showSystemApps || !app.isSystem || app.isLaunchable || app.packageName in selected) &&
            (search.isEmpty() ||
                app.label.contains(search, ignoreCase = true) ||
                app.packageName.contains(search, ignoreCase = true))
    }
}
