package com.sabifox.vpn.vpn

import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.platform.SystemClock
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Reads live traffic totals from the engine's local, secret-protected controller. */
internal class NativeTrafficMonitor(
    private val scope: CoroutineScope,
    private val controllerSecret: String,
    private val publish: (VpnLiveStats) -> Unit,
) {
    private var job: Job? = null

    private val client =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

    fun start() {
        stop()
        job =
            scope.launch(Dispatchers.IO) {
                val sampler = TrafficRateSampler()
                var failures = 0
                while (isActive) {
                    try {
                        val request =
                            Request.Builder()
                                .url("http://${SettingsStore.DEFAULT_SOCKS_BIND_ADDRESS}:${SettingsStore.CONTROLLER_PORT}/connections")
                                .header("Authorization", "Bearer $controllerSecret")
                                .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = JSONObject(response.body?.string().orEmpty())
                                publish(
                                    sampler.sample(
                                        body.optLong("uploadTotal", 0L),
                                        body.optLong("downloadTotal", 0L),
                                        SystemClock.elapsedRealtime(),
                                    )
                                )
                                failures = 0
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (++failures == 3) AppLogger.w("TrafficMonitor", "statistics are temporarily unavailable", error)
                    }
                    delay(1_000L)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
