package com.sabifox.vpn.platform.windows

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.AppLanguage
import com.sabifox.vpn.platform.AppPaths
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs SabiFoxSignIn.exe, a small WebView2 window, and talks to it with JSON lines over
 * stdin/stdout. WebView2 is the Microsoft Edge engine that ships with Windows 10 and 11.
 */
class SignInHelper {
    data class Cookie(val name: String, val value: String, val domain: String)

    interface Listener {
        fun onReady()
        fun onBridge(source: String, data: String)
        fun onUrl(url: String)
        fun onLoaded(url: String)
        fun onCookies(url: String, cookies: List<Cookie>)
        fun onClosed()
        fun onFailed(message: String, runtimeMissing: Boolean)
    }

    @Volatile private var process: Process? = null
    @Volatile private var stdin: BufferedWriter? = null
    @Volatile private var closing = false

    val isRunning: Boolean
        get() = process?.isAlive == true

    fun start(
        url: String,
        userAgent: String,
        script: String,
        title: String,
        cookies: List<Triple<String, String, String>>,
        listener: Listener,
    ) {
        val exe = AppPaths.resource("signin/SabiFoxSignIn.exe")
        if (exe == null) {
            listener.onFailed("The sign-in window (SabiFoxSignIn.exe) is missing. Reinstall SABI-FOX.", false)
            return
        }
        thread(isDaemon = true, name = "signin-helper") {
            val started =
                runCatching {
                        ProcessBuilder(exe.absolutePath).directory(exe.parentFile).redirectErrorStream(false).start()
                    }
                    .getOrElse {
                        listener.onFailed(it.message ?: "Could not open the sign-in window", false)
                        return@thread
                    }
            process = started
            val writer = started.outputStream.bufferedWriter(Charsets.UTF_8)
            stdin = writer
            thread(isDaemon = true, name = "signin-helper-err") {
                runCatching { started.errorStream.bufferedReader().forEachLine { AppLogger.d("SignIn", it) } }
            }
            val init =
                JSONObject()
                    .put("cmd", "init")
                    .put("url", url)
                    .put("ua", userAgent)
                    .put("script", script)
                    .put("title", title)
                    .put("rtl", com.sabifox.vpn.SabiFoxApp.get().desktopSettings.language.isRtl())
                    .put("userData", AppPaths.webView.absolutePath)
                    .put(
                        "cookies",
                        JSONArray().apply {
                            cookies.forEach { (domain, name, value) ->
                                put(JSONObject().put("domain", domain).put("name", name).put("value", value))
                            }
                        },
                    )
            if (!send(init)) {
                listener.onFailed("Could not open the sign-in window", false)
                return@thread
            }
            var failed = false
            runCatching {
                started.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val event = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    when (event.optString("type")) {
                        "ready" -> listener.onReady()
                        "bridge" -> listener.onBridge(event.optString("source"), event.optString("data"))
                        "url" -> listener.onUrl(event.optString("url"))
                        "loaded" -> listener.onLoaded(event.optString("url"))
                        "cookies" -> {
                            val list = event.optJSONArray("cookies") ?: JSONArray()
                            listener.onCookies(
                                event.optString("url"),
                                (0 until list.length()).map {
                                    val c = list.getJSONObject(it)
                                    Cookie(c.optString("name"), c.optString("value"), c.optString("domain"))
                                }.filter { it.name.isNotEmpty() },
                            )
                        }
                        "external" -> WindowsShell.openUrl(event.optString("url"))
                        "error" -> {
                            failed = true
                            listener.onFailed(
                                event.optString("message").ifBlank { "The sign-in window failed" },
                                event.optString("code") == "webview2_missing",
                            )
                        }
                    }
                }
            }
            runCatching { started.waitFor(5, TimeUnit.SECONDS) }
            if (!closing && !failed) listener.onClosed()
        }
    }

    fun evaluate(script: String): Boolean = send(JSONObject().put("cmd", "eval").put("script", script))

    fun requestCookies(): Boolean = send(JSONObject().put("cmd", "cookies"))

    fun focus(): Boolean = send(JSONObject().put("cmd", "focus"))

    @Synchronized
    private fun send(message: JSONObject): Boolean {
        val writer = stdin ?: return false
        return runCatching {
                writer.write(message.toString())
                writer.newLine()
                writer.flush()
                true
            }
            .getOrDefault(false)
    }

    fun close() {
        closing = true
        send(JSONObject().put("cmd", "close"))
        runCatching { stdin?.close() }
        val p = process ?: return
        thread(isDaemon = true) {
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }
}
