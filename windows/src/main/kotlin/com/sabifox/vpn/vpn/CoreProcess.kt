package com.sabifox.vpn.vpn

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.platform.AppPaths
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * The sing-box engine (sabifox-core.exe, built from the same pinned MASQUE fork the Android
 * app uses) runs as a child process. The configuration and the proxy credential travel over
 * stdin only, never touching the disk. When SABI-FOX exits or crashes the pipe closes and the
 * engine shuts down cleanly, removing its routes and restoring the system proxy.
 */
internal class CoreProcess private constructor(
    private val process: Process,
    private val stdin: BufferedWriter,
) {
    @Volatile private var closed = false

    val isAlive: Boolean
        get() = process.isAlive

    @Synchronized
    fun setBearerToken(token: String) {
        if (closed) return
        runCatching {
                stdin.write(JSONObject().put("cmd", "token").put("value", token).toString())
                stdin.newLine()
                stdin.flush()
            }
            .onFailure { AppLogger.w(TAG, "could not hand the renewed credential to the core", it) }
    }

    fun stop() {
        synchronized(this) {
            if (closed) return
            closed = true
            runCatching {
                stdin.write("""{"cmd":"stop"}""")
                stdin.newLine()
                stdin.flush()
            }
            runCatching { stdin.close() }
        }
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            AppLogger.w(TAG, "core did not stop in time; terminating it")
            process.destroyForcibly()
            process.waitFor(3, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val TAG = "CoreProcess"

        fun executable(): File =
            AppPaths.resource("sabifox-core.exe")
                ?: throw IllegalStateException("The SABI-FOX engine (sabifox-core.exe) is missing. Reinstall SABI-FOX.")

        private fun compact(config: String): JSONObject = JSONObject(config)

        /** Validates a configuration without starting anything. */
        suspend fun check(config: String) =
            withContext(Dispatchers.IO) {
                val process =
                    ProcessBuilder(executable().absolutePath, "check")
                        .directory(AppPaths.runtime)
                        .redirectErrorStream(true)
                        .start()
                process.outputStream.bufferedWriter().use {
                    it.write(compact(config).toString())
                    it.newLine()
                }
                val output = process.inputStream.bufferedReader().readText().trim()
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    throw IllegalStateException("The engine did not finish validating the configuration")
                }
                if (process.exitValue() != 0) {
                    throw IllegalArgumentException(
                        "Invalid VPN configuration: " + output.lineSequence().lastOrNull().orEmpty()
                    )
                }
            }

        /**
         * Starts the engine and waits until every inbound is listening. [onExit] is called once
         * if the process ends for any reason after a successful start.
         */
        suspend fun start(
            config: String,
            bearerToken: String,
            onExit: (exitCode: Int, lastError: String?) -> Unit,
        ): CoreProcess =
            withContext(Dispatchers.IO) {
                val process =
                    ProcessBuilder(executable().absolutePath, "run")
                        .directory(AppPaths.runtime)
                        .start()
                val stdin = process.outputStream.bufferedWriter()
                val started = CompletableDeferred<Unit>()
                val lastErrorRef = java.util.concurrent.atomic.AtomicReference<String?>(null)

                thread(isDaemon = true, name = "core-stderr") {
                    runCatching {
                        process.errorStream.bufferedReader().forEachLine { line -> logCoreLine(line) }
                    }
                }
                thread(isDaemon = true, name = "core-events") {
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            val event = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                            when (event.optString("event")) {
                                "started" -> started.complete(Unit)
                                "error" -> {
                                    val message = event.optString("message").ifBlank { "engine error" }
                                    lastErrorRef.set(message)
                                    if (!started.isCompleted)
                                        started.completeExceptionally(IllegalStateException(message))
                                    else AppLogger.e(TAG, "core: $message")
                                }
                            }
                        }
                    }
                    val code = runCatching { process.waitFor() }.getOrDefault(-1)
                    if (!started.isCompleted) {
                        started.completeExceptionally(
                            IllegalStateException(lastErrorRef.get() ?: "The engine stopped during start (exit code $code)")
                        )
                    } else {
                        onExit(code, lastErrorRef.get())
                    }
                }

                try {
                    stdin.write(
                        JSONObject().put("token", bearerToken).put("config", compact(config)).toString()
                    )
                    stdin.newLine()
                    stdin.flush()
                    withTimeout(45_000L) { started.await() }
                } catch (error: Throwable) {
                    runCatching { stdin.close() }
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
                    throw error
                }
                CoreProcess(process, stdin)
            }

        private val levelPattern = Regex("""\b(TRACE|DEBUG|INFO|WARN|ERROR|FATAL|PANIC)\b""")

        private fun logCoreLine(raw: String) {
            val line = raw.replace(Regex("\u001B\\[[;\\d]*m"), "").trim()
            if (line.isEmpty()) return
            when (levelPattern.find(line)?.value) {
                "ERROR", "FATAL", "PANIC" -> AppLogger.e("core", line)
                "WARN" -> AppLogger.w("core", line)
                "INFO" -> AppLogger.i("core", line)
                else -> AppLogger.d("core", line)
            }
        }
    }
}
