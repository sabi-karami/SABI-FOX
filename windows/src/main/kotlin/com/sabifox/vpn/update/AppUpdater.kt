package com.sabifox.vpn.update

import com.sabifox.vpn.BuildConfig
import com.sabifox.vpn.data.AppLinks
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.awaitResponse
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.platform.AppPaths
import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.windows.WindowsShell
import com.sabifox.vpn.vpn.SabiFoxService
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val FAILED_CHECK_BACKOFF_MS = 15 * 60 * 1000L
private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
private const val UPDATER_TAG = "AppUpdater"
internal const val UPDATE_PREFS = "sabifox_updates"
internal const val KEY_RECONNECT_AFTER_UPDATE = "reconnect_after_update"

internal sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object Current : UpdateState

    data class Available(val release: AppRelease) : UpdateState

    data class Downloading(val bytes: Long, val total: Long) : UpdateState

    data class Ready(val file: File, val version: String, val sha256: String) : UpdateState

    data class Failed(val message: String) : UpdateState
}

/**
 * Windows updater. Same flow as Android: check this repository's latest release, download the
 * build for this installation (installer or portable zip), verify it against the signed
 * SHA256SUMS-windows.txt, then install it and start SABI-FOX again.
 */
internal class AppUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    private val directory = File(AppPaths.cache, "updates").apply { mkdirs() }
    private var operation: Job? = null
    private val publicationLock = Any()
    private var generation = 0L
    private val mutablePromptVisible = MutableStateFlow(false)
    val promptVisible: StateFlow<Boolean> = mutablePromptVisible

    /** Set by the window so the updater can quit SABI-FOX before the installer runs. */
    @Volatile var exitForUpdate: (suspend () -> Unit)? = null

    fun showPrompt() {
        mutablePromptVisible.value = true
    }

    fun hidePrompt() {
        mutablePromptVisible.value = false
    }

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState

    var automaticChecks: Boolean
        get() = prefs.getBoolean("automatic_checks", true)
        set(value) {
            prefs.edit().putBoolean("automatic_checks", value).apply()
        }

    var autoInstall: Boolean
        get() = prefs.getBoolean("auto_install", true)
        set(value) {
            prefs.edit().putBoolean("auto_install", value).apply()
        }

    /** Updates install through the signed installer or by swapping the portable folder. */
    fun canInstallPackages(): Boolean = AppPaths.launcherExe != null

    /** Whether SABI-FOX should reconnect once because it was connected before an update. */
    fun consumeReconnectAfterUpdate(): Boolean {
        val value = prefs.getBoolean(KEY_RECONNECT_AFTER_UPDATE, false)
        if (value) prefs.edit().putBoolean(KEY_RECONNECT_AFTER_UPDATE, false).apply()
        return value
    }

    init {
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Keeps checking while SABI-FOX runs in the tray for days. */
    fun startPeriodicChecks() {
        scope.launch {
            while (isActive) {
                delay(6 * 60 * 60 * 1000L)
                if (autoInstall) runAutomaticUpdate() else if (automaticChecks) check(automatic = true)
            }
        }
    }

    fun launchAutomaticUpdate() {
        scope.launch { runAutomaticUpdate() }
    }

    private suspend fun runAutomaticUpdate() {
        if (!autoInstall || operation?.isActive == true || !canInstallPackages()) {
            if (automaticChecks) check(automatic = true)
            return
        }
        if (mutableState.value is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_successful_check", 0) in 0 until CHECK_INTERVAL_MS) return
        val job =
            scope.launch {
                try {
                    val release = discoverRelease() ?: run {
                        mutableState.value = UpdateState.Current
                        return@launch
                    }
                    val ready = fetchVerified(release, synchronized(publicationLock) { ++generation })
                    if (SabiFoxService.state.value != ConnectionState.DISCONNECTED) {
                        // Do not drop an active tunnel unannounced; offer the install instead.
                        mutableState.value = ready
                        showPrompt()
                        return@launch
                    }
                    install(ready)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLogger.w(UPDATER_TAG, "auto-update failed", error)
                    mutableState.value = UpdateState.Idle
                }
            }
        operation = job
        job.join()
    }

    fun check(automatic: Boolean = false) {
        if (operation?.isActive == true) return
        if (automatic) {
            if (!automaticChecks) return
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("last_successful_check", 0) in 0 until CHECK_INTERVAL_MS) return
            if (now - prefs.getLong("last_failed_check", 0) in 0 until FAILED_CHECK_BACKOFF_MS) return
        }
        val myGeneration = synchronized(publicationLock) { ++generation }
        mutableState.value = UpdateState.Checking
        operation =
            scope.launch {
                try {
                    val release = discoverRelease()
                    if (myGeneration != generation) return@launch
                    if (release == null) {
                        mutableState.value = UpdateState.Current
                    } else {
                        mutableState.value = UpdateState.Available(release)
                        if (automatic) showPrompt()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    prefs.edit().putLong("last_failed_check", System.currentTimeMillis()).apply()
                    AppLogger.w(UPDATER_TAG, "update check failed", error)
                    if (myGeneration == generation) {
                        mutableState.value = if (automatic) UpdateState.Idle else UpdateState.Failed(error.message ?: "Update check failed")
                    }
                }
            }
    }

    fun download(release: AppRelease) {
        val myGeneration = synchronized(publicationLock) { ++generation }
        operation?.cancel()
        operation =
            scope.launch {
                try {
                    mutableState.value = fetchVerified(release, myGeneration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLogger.w(UPDATER_TAG, "update download failed", error)
                    if (myGeneration == generation) mutableState.value = UpdateState.Failed(error.message ?: "Download failed")
                }
            }
    }

    fun cancel() {
        synchronized(publicationLock) { ++generation }
        operation?.cancel()
        operation = null
        (mutableState.value as? UpdateState.Ready)?.file?.delete()
        mutableState.value = UpdateState.Idle
    }

    suspend fun installVerified(ready: UpdateState.Ready) {
        withContext(Dispatchers.IO) {
            check(sha256(ready.file) == ready.sha256) { "The downloaded update changed on disk; download it again" }
        }
        install(ready)
    }

    private suspend fun install(ready: UpdateState.Ready) {
        val wasConnected = SabiFoxService.state.value != ConnectionState.DISCONNECTED
        prefs.edit().putBoolean(KEY_RECONNECT_AFTER_UPDATE, wasConnected).commit()
        withContext(Dispatchers.IO) {
            if (AppPaths.portable) launchPortableSwap(ready.file) else launchInstaller(ready.file)
        }
        AppLogger.i(UPDATER_TAG, "installing ${ready.version}; SABI-FOX restarts when it finishes")
        exitForUpdate?.invoke()
    }

    private fun launchInstaller(file: File) {
        ProcessBuilder(
                file.absolutePath,
                "/SILENT",
                "/SUPPRESSMSGBOXES",
                "/NORESTART",
                "/CLOSEAPPLICATIONS",
                "/LANG=" + (if (java.util.Locale.getDefault().language == "fa") "persian" else "english"),
            )
            .directory(file.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .outputStream
            .close()
    }

    /** Unpacks the new portable build and swaps it in after SABI-FOX exits, keeping `data`. */
    private fun launchPortableSwap(zip: File) {
        val installDir = checkNotNull(AppPaths.installDir) { "Portable folder not found" }
        val staging = File(directory, "portable").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(staging, entry.name).canonicalFile
                check(target.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe path in update archive" }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
        val root = staging.listFiles()?.singleOrNull { File(it, "SABI-FOX.exe").isFile } ?: staging
        check(File(root, "SABI-FOX.exe").isFile) { "The update archive does not contain SABI-FOX.exe" }
        // jpackage runs the JVM in a child of the SABI-FOX.exe that was started; wait for both.
        val self = ProcessHandle.current()
        val pids =
            listOf(self.pid()) +
                self.parent()
                    .filter { it.info().command().orElse("").endsWith("SABI-FOX.exe", ignoreCase = true) }
                    .map { listOf(it.pid()) }
                    .orElse(emptyList())
        val lines =
            listOf("@echo off", "chcp 65001 >nul", ":wait", "ping -n 2 127.0.0.1 >nul") +
                pids.map { pid -> "tasklist /FI \"PID eq $pid\" 2>nul | find \"$pid\" >nul && goto wait" } +
                listOf(
                    "robocopy \"${root.absolutePath}\" \"${installDir.absolutePath}\" /E /R:5 /W:1 /XD data /NFL /NDL /NJH /NJS >nul",
                    "start \"\" \"${File(installDir, "SABI-FOX.exe").absolutePath}\"",
                )
        val script = File(directory, "apply-update.cmd")
        script.writeText(lines.joinToString("\r\n", postfix = "\r\n"))
        ProcessBuilder("cmd.exe", "/c", script.absolutePath)
            .directory(directory)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .outputStream
            .close()
    }

    private suspend fun discoverRelease(): AppRelease? {
        val request =
            Request.Builder()
                .url(AppLinks.RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SABI-FOX-Windows/${BuildConfig.VERSION_NAME}")
                .build()
        val json =
            client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
        prefs.edit().putLong("last_successful_check", System.currentTimeMillis()).apply()
        return parseWindowsRelease(json, BuildConfig.VERSION_NAME, AppPaths.portable)
    }

    private suspend fun fetchBytes(asset: UpdateAsset, limit: Int): ByteArray {
        val request = Request.Builder().url(asset.url).header("User-Agent", "SABI-FOX-Windows").build()
        return client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Download of ${asset.name} failed (HTTP ${response.code})")
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.size > limit) throw IOException("${asset.name} is too large")
            bytes
        }
    }

    private suspend fun fetchVerified(release: AppRelease, myGeneration: Long): UpdateState.Ready {
        mutableState.value = UpdateState.Downloading(0, release.asset.size)
        val sums = fetchBytes(release.checksums, 64 * 1024)
        val signature = fetchBytes(release.signature, 1024)
        check(verifyChecksumSignature(sums, signature)) { "The update signature is invalid; it was not installed" }
        val expected =
            checkNotNull(checksumFor(sums.toString(Charsets.UTF_8), release.asset.name)) {
                "The release does not list a checksum for ${release.asset.name}"
            }
        release.asset.digest?.let { check(it.equals(expected, true)) { "GitHub's checksum does not match the signed one" } }
        val file = File(directory, release.asset.name)
        val request = Request.Builder().url(release.asset.url).header("User-Agent", "SABI-FOX-Windows").build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.asset.size
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        read += n
                        if (read > MAX_UPDATE_BYTES) throw IOException("The update is larger than expected")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        if (read - lastReport > 256 * 1024) {
                            lastReport = read
                            if (myGeneration != generation) throw CancellationException("superseded")
                            mutableState.value = UpdateState.Downloading(read, total)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, true)) {
            file.delete()
            throw IOException("The download is corrupted (checksum mismatch)")
        }
        return UpdateState.Ready(file, release.tag.removePrefix("v"), actual)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Opens the release page when an update cannot be installed in place (developer runs). */
    fun openReleasePage() {
        WindowsShell.openUrl("${AppLinks.SOURCE}/releases/latest")
    }
}
