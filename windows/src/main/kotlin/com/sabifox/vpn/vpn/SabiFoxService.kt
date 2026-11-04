package com.sabifox.vpn.vpn

import com.sabifox.vpn.AppContainer
import com.sabifox.vpn.SabiFoxApp
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.ControlPlaneHttp
import com.sabifox.vpn.data.FxaRefreshFailure
import com.sabifox.vpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.sabifox.vpn.data.GuardianClient
import com.sabifox.vpn.data.GuardianHttpError
import com.sabifox.vpn.data.ProxyPass
import com.sabifox.vpn.data.ProxyStateStore
import com.sabifox.vpn.data.QuotaExceededError
import com.sabifox.vpn.data.SecureStorageException
import com.sabifox.vpn.data.ServerListClient
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.TokenInvalidError
import com.sabifox.vpn.data.TunnelSettings
import com.sabifox.vpn.data.awaitResponse
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.data.model.ProxyCandidate
import com.sabifox.vpn.data.model.RECOMMENDED_COUNTRY_CODE
import com.sabifox.vpn.data.preferredConnectionCandidates
import com.sabifox.vpn.data.retryDeadlineMillis
import com.sabifox.vpn.data.retryDelayMillis
import com.sabifox.vpn.data.tunnelSnapshot
import com.sabifox.vpn.platform.AppPaths
import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.SystemClock
import com.sabifox.vpn.platform.windows.SystemProxyGuard
import com.sabifox.vpn.platform.windows.WindowsShell
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class VpnLiveStats(
    val txRateBytesPerSec: Long = 0L,
    val rxRateBytesPerSec: Long = 0L,
    val sessionBytes: Long = 0L,
)

/** Thrown when the system-wide tunnel is requested without administrator rights. */
class ElevationRequiredException :
    IllegalStateException("The system-wide VPN needs administrator rights. Restart SABI-FOX as administrator or use proxy-only mode.")

private const val TAG = "SabiFoxService"
private const val SERVER_LIST_FETCH_TIMEOUT_MS = 15_000L
private const val PROXY_PASS_FETCH_BUDGET_MS = 60_000L
private const val PROXY_PASS_RETRY_MS = 5_000L

private fun proxyPassDeadline(expiresAtEpochSeconds: Long?): Long =
    credentialDeadlineMillis(expiresAtEpochSeconds, System.currentTimeMillis(), SystemClock.elapsedRealtime())

/**
 * Desktop counterpart of the Android VpnService. It keeps the same public state (so every
 * screen is shared with Android) and the same connection logic: mint a Guardian proxy pass,
 * build the sing-box configuration, start the engine, renew the pass before it expires, watch
 * tunnel health and recover automatically.
 */
object SabiFoxService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val opMutex = Mutex()
    private val pingMutex = Mutex()
    private val credentialCommitLock = Any()
    private val recoveryBudget = RecoveryBudget()

    private lateinit var app: SabiFoxApp

    @Volatile private var core: CoreProcess? = null
    private var connectJob: Job? = null
    private var retryJob: Job? = null
    private var trafficMonitor: NativeTrafficMonitor? = null
    private var pingJob: Job? = null
    private var exitCheckJob: Job? = null
    private var healthJob: Job? = null
    private var tokenRenewalJob: Job? = null
    private var credentialExpiryJob: Job? = null
    private var connectionSettings: TunnelSettings? = null
    private var connectionCandidate: ProxyCandidate? = null
    private var directoryRefreshAfterElapsedMs = 0L
    @Volatile private var activeSocksPort: Int? = null
    @Volatile private var activeProxyCredentials: Pair<String, String> = "" to ""
    private val installedCredentialDeadline = AtomicLong(0L)
    private val connectionGeneration = AtomicInteger()
    @Volatile private var stopping = false
    private var healthRecoveryInProgress = false
    private var reconnectAttempt = 0
    private val retryNotBeforeElapsedMs = AtomicLong(0L)
    @Volatile private var controllerSecret = UUID.randomUUID().toString()

    private val _proxyOnlyActive = MutableStateFlow(false)
    val proxyOnlyActive: StateFlow<Boolean> = _proxyOnlyActive

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _vpnLiveStats = MutableStateFlow(VpnLiveStats())
    val vpnLiveStats: StateFlow<VpnLiveStats> = _vpnLiveStats

    private val _connectedAtElapsedMs = MutableStateFlow<Long?>(null)
    val connectedAtElapsedMs: StateFlow<Long?> = _connectedAtElapsedMs

    private val _pingLatencyMs = MutableStateFlow<Int?>(null)
    val pingLatencyMs: StateFlow<Int?> = _pingLatencyMs

    private val _activeCandidate = MutableStateFlow<ProxyCandidate?>(null)
    val activeCandidate: StateFlow<ProxyCandidate?> = _activeCandidate

    private val _tunnelHealth = MutableStateFlow(TunnelHealth.UNKNOWN)
    val tunnelHealth: StateFlow<TunnelHealth> = _tunnelHealth

    private val _vpnEdgeIp = MutableStateFlow<IpLocationInfo?>(null)
    val vpnEdgeIp: StateFlow<IpLocationInfo?> = _vpnEdgeIp

    /** The local proxy address while proxy-only mode is active, e.g. "127.0.0.1:1080". */
    private val _localProxyAddress = MutableStateFlow<String?>(null)
    val localProxyAddress: StateFlow<String?> = _localProxyAddress

    fun attach(application: SabiFoxApp) {
        app = application
        application.tokenStore.onSessionInvalidated = { requestDisconnect("account session was cleared") }
    }

    suspend fun pingNow(): Int? = measureActiveLatency()

    fun start(@Suppress("UNUSED_PARAMETER") context: Context) {
        stopping = false
        if (_state.value == ConnectionState.DISCONNECTED) {
            _lastError.value = null
            _state.value = ConnectionState.CONNECTING
            reconnectAttempt = 0
            retryNotBeforeElapsedMs.set(0L)
            connectionSettings = null
            connectionCandidate = null
        }
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { opMutex.withLock { connect() } }
    }

    fun stop(@Suppress("UNUSED_PARAMETER") context: Context) = requestDisconnect("user request")

    /** Stops the tunnel and waits for the engine to exit (used before quitting the app). */
    suspend fun shutdown() {
        requestDisconnect("application exit")
        opMutex.withLock { teardown() }
    }

    private fun requestDisconnect(reason: String) {
        AppLogger.i(TAG, "disconnect: $reason")
        stopping = true
        connectionGeneration.incrementAndGet()
        retryJob?.cancel()
        retryJob = null
        connectJob?.cancel()
        connectJob = null
        scope.launch {
            opMutex.withLock {
                teardown()
                _state.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun ensureGenerationCurrent(generation: Int) {
        if (stopping || generation != connectionGeneration.get()) throw CancellationException("Connection attempt superseded")
    }

    private fun isCurrentSession(generation: Int): Boolean =
        !stopping && generation == connectionGeneration.get() && _state.value == ConnectionState.CONNECTED

    private fun isFatalUpstreamError(error: Throwable): Boolean =
        error is TokenInvalidError ||
            error is QuotaExceededError ||
            error is IllegalArgumentException ||
            error is ElevationRequiredException ||
            error is SecureStorageException ||
            (error is FxaRefreshFailure && error.permanent) ||
            (error is GuardianHttpError && error.statusCode in listOf(401, 403))

    private fun validateProxyPass(pass: ProxyPass) {
        require(pass.token.isNotBlank() && !pass.token.contains('\r') && !pass.token.contains('\n')) {
            "Guardian returned an invalid proxy credential"
        }
        val expiry = pass.expiresAtEpochSeconds
        require(expiry == null || expiry - System.currentTimeMillis() / 1_000L > 60L) {
            "Guardian returned a proxy credential with insufficient lifetime"
        }
    }

    private suspend fun connect() {
        if (_state.value == ConnectionState.CONNECTED || stopping) return
        val retryWait = (retryNotBeforeElapsedMs.get() - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        if (retryWait > 0) {
            connectJob = null
            scheduleReconnect(retryWait)
            return
        }
        val myGeneration = connectionGeneration.incrementAndGet()
        runCatching {
                AppLogger.i(TAG, "connect: starting sing-box engine")
                val tokenStore = app.tokenStore
                val sessionEpoch = tokenStore.sessionEpoch
                if (tokenStore.loadAuth() == null) {
                    tokenStore.storageError.value?.let { throw SecureStorageException(IllegalStateException(it)) }
                    throw TokenInvalidError("Not signed in")
                }
                val settingsStore = app.settingsStore
                val tunnelSettings = connectionSettings ?: settingsStore.tunnelSnapshot().also { connectionSettings = it }
                val proxyOnlyMode = tunnelSettings.proxyOnlyMode
                if (!proxyOnlyMode && !WindowsShell.isElevated) throw ElevationRequiredException()
                controllerSecret = UUID.randomUUID().toString()

                val directoryClient = app.serverListClient
                val existingCandidate = connectionCandidate
                val refreshRetry =
                    existingCandidate != null &&
                        reconnectAttempt > 0 &&
                        SystemClock.elapsedRealtime() >= directoryRefreshAfterElapsedMs &&
                        !directoryClient.hasFreshCache()
                val primaryCandidate =
                    if (existingCandidate == null || refreshRetry) {
                        directoryRefreshAfterElapsedMs = SystemClock.elapsedRealtime() + 60_000L
                        resolveConnectCandidate(app.proxyStateStore, directoryClient, existingCandidate).also {
                            connectionCandidate = it
                        }
                    } else existingCandidate
                ensureGenerationCurrent(myGeneration)

                val socksPort = tunnelSettings.socksPort
                ListenerAvailability.requireAvailable(listOf(socksPort, SettingsStore.CONTROLLER_PORT))
                _proxyOnlyActive.value = proxyOnlyMode

                suspend fun mintProxyPass(): ProxyPass {
                    var auth = app.authRepository.ensureFreshAccessToken()
                    if (tokenStore.sessionEpoch != sessionEpoch) throw TokenInvalidError("Session changed")
                    val guardian = GuardianClient()
                    val pass =
                        try {
                            guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                        } catch (quota: QuotaExceededError) {
                            settingsStore.saveAccountQuota(false, quota.quotaRemaining, quota.quotaMax)
                            throw quota
                        } catch (rejected: TokenInvalidError) {
                            AppLogger.w(TAG, "Guardian rejected the OAuth access token; renewing it and retrying", rejected)
                            val renewed = app.authRepository.refreshAccessToken()
                            if (tokenStore.sessionEpoch != sessionEpoch) throw TokenInvalidError("Session changed")
                            if (renewed != null) {
                                auth = renewed
                                try {
                                    guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                                } catch (stillRejected: TokenInvalidError) {
                                    AppLogger.w(TAG, "still rejected; activating the Guardian entitlement and retrying", stillRejected)
                                    guardian.activateGuardian(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                                    guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                                }
                            } else {
                                AppLogger.w(TAG, "token renewal failed; activating Guardian entitlement and retrying")
                                guardian.activateGuardian(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                                guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, auth.accessToken)
                            }
                        }
                    val hasQuota = pass.quotaRemaining != null || pass.quotaMax != null
                    val isUnlimited = if (hasQuota) false else settingsStore.cachedIsUnlimited
                    settingsStore.saveAccountQuota(isUnlimited, pass.quotaRemaining, pass.quotaMax)
                    return pass
                }

                val pass = mintProxyPass()
                validateProxyPass(pass)
                val lifetimeNote =
                    pass.expiresAtEpochSeconds?.let { " (valid for ${(it * 1_000L - System.currentTimeMillis()) / 1_000L}s)" }
                        ?: " (lifetime not stated)"
                AppLogger.i(TAG, "connect: acquired Guardian proxy pass$lifetimeNote")
                val initialDeadline = proxyPassDeadline(pass.expiresAtEpochSeconds)
                installedCredentialDeadline.set(initialDeadline)
                startCredentialExpiryWatchdog(myGeneration)

                val baseConfig =
                    SingBoxConfigBuilder.build(
                        primaryCandidate = primaryCandidate,
                        settings = tunnelSettings,
                        bearerToken = pass.token,
                        appPackageName = null,
                        controllerSecret = controllerSecret,
                    )
                val setSystemProxy = proxyOnlyMode && app.desktopSettings.systemProxy
                val configContent =
                    WindowsConfigAdapter.adapt(
                        baseConfig,
                        tunnelSettings,
                        setSystemProxy = setSystemProxy,
                        ownExecutable = AppPaths.launcherExe?.absolutePath,
                    )
                ensureGenerationCurrent(myGeneration)

                if (setSystemProxy) SystemProxyGuard.armed("127.0.0.1:$socksPort")
                val engine =
                    CoreProcess.start(configContent, pass.token) { code, message ->
                        onCoreExited(myGeneration, code, message)
                    }
                core = engine
                ensureGenerationCurrent(myGeneration)

                synchronized(credentialCommitLock) {
                    ensureGenerationCurrent(myGeneration)
                    _connectedAtElapsedMs.value = SystemClock.elapsedRealtime()
                    _lastError.value = null
                    _activeCandidate.value = primaryCandidate
                    _tunnelHealth.value = TunnelHealth.CHECKING
                    _localProxyAddress.value = if (proxyOnlyMode) "${tunnelSettings.socksBindAddress}:$socksPort" else null
                    _state.value = ConnectionState.CONNECTED
                }
                reconnectAttempt = 0
                healthRecoveryInProgress = false
                retryNotBeforeElapsedMs.set(0L)
                AppLogger.i(TAG, "connect: CONNECTED via sing-box core to ${primaryCandidate.authority}")

                val proxyCredentials =
                    if (tunnelSettings.localProxyAuthEnabled) tunnelSettings.localProxyUsername to tunnelSettings.localProxyPassword
                    else "" to ""
                activeProxyCredentials = proxyCredentials
                activeSocksPort = socksPort
                pingJob = scope.launch { measureActiveLatency() }
                startProxyPassRenewal(myGeneration, initialDeadline, sessionEpoch) { mintProxyPass() }
                startExitCheck(myGeneration, socksPort, proxyCredentials, primaryCandidate)
                startSpeedUpdates(myGeneration)
                startHealthMonitor(myGeneration, tunnelSettings, primaryCandidate)
            }
            .onFailure { error ->
                if (error is CancellationException && error !is TimeoutCancellationException) {
                    teardown()
                    throw error
                }
                teardown()
                if (stopping || myGeneration != connectionGeneration.get()) return
                AppLogger.e(TAG, "connect failed", error)
                val failedAutomaticRecovery = healthRecoveryInProgress
                healthRecoveryInProgress = false
                _lastError.value =
                    if (failedAutomaticRecovery) "Automatic recovery failed. Reconnect to retry."
                    else friendlyErrorMessage(error)
                if (!failedAutomaticRecovery && !isFatalUpstreamError(error)) {
                    reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
                    val retryDelay =
                        retryDelayMillis(
                            minOf(60_000L, 1_000L shl reconnectAttempt),
                            (error as? GuardianHttpError)?.retryAfterMs,
                            kotlin.random.Random.nextLong(250L, 1_000L),
                        )
                    retryNotBeforeElapsedMs.set(retryDeadlineMillis(SystemClock.elapsedRealtime(), retryDelay))
                    AppLogger.w(TAG, "transient connect failure; retrying in ${retryDelay / 1_000L}s")
                    _state.value = ConnectionState.CONNECTING
                    connectJob = null
                    scheduleReconnect(retryDelay)
                    return
                }
                stopping = true
                _state.value = ConnectionState.DISCONNECTED
            }
        connectJob = null
    }

    private fun onCoreExited(generation: Int, code: Int, message: String?) {
        if (stopping || generation != connectionGeneration.get()) return
        AppLogger.e(TAG, "the engine stopped unexpectedly (exit code $code)${message?.let { ": $it" } ?: ""}")
        scope.launch {
            opMutex.withLock {
                if (stopping || generation != connectionGeneration.get()) return@withLock
                teardown()
                _lastError.value = message ?: "The VPN engine stopped unexpectedly. Reconnecting…"
                _state.value = ConnectionState.CONNECTING
                reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
                scheduleReconnect(minOf(30_000L, 1_000L shl reconnectAttempt))
            }
        }
    }

    private fun scheduleReconnect(delayMillis: Long) {
        if (retryJob?.isActive == true || stopping) return
        retryJob =
            scope.launch {
                try {
                    delay(delayMillis)
                    if (!stopping && _state.value == ConnectionState.CONNECTING && connectJob?.isActive != true) {
                        connectJob = scope.launch { opMutex.withLock { connect() } }
                    }
                } finally {
                    retryJob = null
                }
            }
    }

    private suspend fun resolveConnectCandidate(
        proxyStateStore: ProxyStateStore,
        client: ServerListClient,
        current: ProxyCandidate?,
    ): ProxyCandidate {
        val saved = current ?: proxyStateStore.load()
        val cached = client.loadCachedCountries()
        val countries =
            if (cached.isNotEmpty() && client.hasFreshCache()) cached
            else
                runCatching { withTimeout(SERVER_LIST_FETCH_TIMEOUT_MS) { client.fetchCountries() } }
                    .getOrElse {
                        if (it is CancellationException && it !is TimeoutCancellationException) throw it
                        AppLogger.w(TAG, "could not refresh server directory; using cached selection", it)
                        cached
                    }
        if (countries.isEmpty() && saved != null) return saved
        val candidates = countries.flatMap { ServerListClient.candidatesForCountry(countries, it.code) }
        val chosen =
            preferredConnectionCandidates(candidates, saved).randomOrNull()
                ?: throw IllegalArgumentException(
                    if (saved == null) "No servers are available. Choose a location first."
                    else "No available servers in the selected location. Choose another location."
                )
        if (current == null) proxyStateStore.saveIfUnchanged(saved, chosen)
        return chosen
    }

    private fun startProxyPassRenewal(
        generation: Int,
        initialDeadline: Long,
        sessionEpoch: Long,
        mintProxyPass: suspend () -> ProxyPass,
    ) {
        tokenRenewalJob?.cancel()
        tokenRenewalJob =
            scope.launch {
                var deadline = initialDeadline
                var nextDelay = (deadline - SystemClock.elapsedRealtime() - PROXY_PASS_FETCH_BUDGET_MS).coerceAtLeast(0L)
                while (isActive) {
                    delay(nextDelay)
                    if (generation != connectionGeneration.get() || stopping) return@launch
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) {
                        _lastError.value = "Proxy credentials could not be renewed in time. Reconnect to retry."
                        requestDisconnect("proxy pass renewal deadline reached")
                        return@launch
                    }
                    try {
                        val pass = withTimeout(minOf(PROXY_PASS_FETCH_BUDGET_MS, remaining)) { mintProxyPass() }
                        app.tokenStore.withCurrentSession(sessionEpoch) {
                            synchronized(credentialCommitLock) {
                                ensureGenerationCurrent(generation)
                                validateProxyPass(pass)
                                core?.setBearerToken(pass.token)
                                installedCredentialDeadline.set(proxyPassDeadline(pass.expiresAtEpochSeconds))
                            }
                        }
                        deadline = installedCredentialDeadline.get()
                        nextDelay = (deadline - SystemClock.elapsedRealtime() - PROXY_PASS_FETCH_BUDGET_MS).coerceAtLeast(0L)
                        AppLogger.i(TAG, "proxy pass replaced without interrupting connections")
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        if (isFatalUpstreamError(error)) {
                            _lastError.value = friendlyErrorMessage(error)
                            requestDisconnect("credential renewal was rejected")
                            return@launch
                        }
                        AppLogger.w(TAG, "proxy pass renewal failed; retrying within its validity deadline", error)
                        nextDelay =
                            minOf(
                                maxOf(PROXY_PASS_RETRY_MS, (error as? GuardianHttpError)?.retryAfterMs ?: 0L),
                                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
                            )
                    }
                }
            }
    }

    private fun startCredentialExpiryWatchdog(generation: Int) {
        credentialExpiryJob?.cancel()
        credentialExpiryJob =
            scope.launch {
                while (isActive) {
                    val remaining = installedCredentialDeadline.get() - SystemClock.elapsedRealtime()
                    if (remaining > 0L) {
                        delay(remaining)
                        continue
                    }
                    if (stopping || generation != connectionGeneration.get()) return@launch
                    _lastError.value = "Proxy credentials expired. Reconnect to retry."
                    requestDisconnect("installed credential deadline reached")
                    return@launch
                }
            }
    }

    private fun startExitCheck(
        generation: Int,
        socksPort: Int,
        credentials: Pair<String, String>,
        candidate: ProxyCandidate,
    ) {
        exitCheckJob =
            scope.launch {
                val exitCheck = ExitCheck()
                repeat(3) { attempt ->
                    if (!isCurrentSession(generation)) return@launch
                    val result = exitCheck.fetchProxiedIpInfo(socksPort, credentials.first, credentials.second)
                    val info = result.getOrNull()
                    if (info != null) {
                        if (isCurrentSession(generation) && _activeCandidate.value == candidate) {
                            _vpnEdgeIp.value = info
                            AppLogger.i(TAG, "exit check: observed ip=${info.ip}, country=${info.countryCode}")
                        }
                        return@launch
                    }
                    AppLogger.w(TAG, "exit check attempt ${attempt + 1} failed (non-fatal)", result.exceptionOrNull())
                    if (attempt < 2) delay(2_000L * (attempt + 1))
                }
            }
    }

    private suspend fun measureActiveLatency(): Int? {
        val generation = connectionGeneration.get()
        val port = activeSocksPort ?: return null
        return pingMutex.withLock {
            fun current() = isCurrentSession(generation) && activeSocksPort == port
            if (!current()) return@withLock null
            val credentials = activeProxyCredentials
            val latency = PingUtil.measureProxyLatencyMs(port, app.settingsStore.latencyProbeUrl, credentials.first, credentials.second)
            if (current()) {
                _pingLatencyMs.value = latency
                latency
            } else null
        }
    }

    private fun startSpeedUpdates(generation: Int) {
        trafficMonitor?.stop()
        trafficMonitor =
            NativeTrafficMonitor(scope, controllerSecret) { stats ->
                    if (isCurrentSession(generation)) _vpnLiveStats.value = stats
                }
                .also { it.start() }
    }

    private suspend fun directInternetWorks(urls: List<String>): Boolean =
        urls.all { url ->
            try {
                ControlPlaneHttp.client.newCall(okhttp3.Request.Builder().url(url).build()).awaitResponse().use { it.isSuccessful || it.code == 204 }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

    private fun startHealthMonitor(generation: Int, settings: TunnelSettings, candidate: ProxyCandidate) {
        healthJob?.cancel()
        healthJob =
            scope.launch {
                var failures = 0
                val credentials = activeProxyCredentials
                val urls = listOf("https://www.gstatic.com/generate_204", "https://www.cloudflare.com/cdn-cgi/trace")
                fun current() = isActive && isCurrentSession(generation)
                delay(5_000L)
                while (current()) {
                    val healthy =
                        urls.any { PingUtil.measureProxyLatencyMs(settings.socksPort, it, credentials.first, credentials.second) != null }
                    if (!current()) return@launch
                    if (!healthy && !directInternetWorks(urls.take(1))) {
                        _tunnelHealth.value = TunnelHealth.NO_NETWORK
                        failures = 0
                        delay(30_000L)
                        continue
                    }
                    _tunnelHealth.value = if (healthy) TunnelHealth.HEALTHY else TunnelHealth.UNREACHABLE
                    failures = if (healthy) 0 else failures + 1
                    if (
                        failures >= 3 &&
                            settings.automaticRecovery &&
                            settings.effectiveCustomEdgeAddress == null &&
                            recoveryBudget.tryAcquire(SystemClock.elapsedRealtime())
                    ) {
                        AppLogger.w(TAG, "tunnel unreachable 3 times in a row; switching to another server in the same country")
                        val directory = app.serverListClient
                        val countries =
                            directory.loadCachedCountries().ifEmpty {
                                runCatching { withTimeout(SERVER_LIST_FETCH_TIMEOUT_MS) { directory.fetchCountries() } }.getOrDefault(emptyList())
                            }
                        val alternate =
                            ServerListClient.candidatesForCountry(countries, candidate.countryCode)
                                .filter { it.host != candidate.host || it.port != candidate.port }
                                .randomOrNull()
                        opMutex.withLock {
                            if (stopping || generation != connectionGeneration.get()) return@withLock
                            teardown()
                            _state.value = ConnectionState.CONNECTING
                            connectionCandidate = alternate ?: candidate
                            healthRecoveryInProgress = true
                            connect()
                        }
                        return@launch
                    }
                    delay(if (healthy) 60_000L else 15_000L)
                }
            }
    }

    private fun friendlyErrorMessage(error: Throwable): String =
        when (error) {
            is QuotaExceededError -> "Your VPN quota is exhausted. Try again later."
            is TokenInvalidError -> "Your session was rejected. Please sign in again."
            is GuardianHttpError ->
                if (error.statusCode in listOf(401, 403)) "Your session was rejected. Please sign in again."
                else error.message ?: "Guardian is temporarily unavailable"
            is TimeoutCancellationException -> "the connection timed out"
            else -> error.message ?: (error::class.simpleName ?: "Connection failed")
        }

    /** Releases everything belonging to the current session. Must run under [opMutex]. */
    private fun teardown() {
        tokenRenewalJob?.cancel()
        credentialExpiryJob?.cancel()
        healthJob?.cancel()
        pingJob?.cancel()
        exitCheckJob?.cancel()
        trafficMonitor?.stop()
        trafficMonitor = null
        val engine = core
        core = null
        if (engine != null) {
            runCatching { engine.stop() }.onFailure { AppLogger.w(TAG, "engine stop failed", it) }
        }
        SystemProxyGuard.disarmed()
        activeSocksPort = null
        activeProxyCredentials = "" to ""
        installedCredentialDeadline.set(0L)
        _activeCandidate.value = null
        _vpnEdgeIp.value = null
        _pingLatencyMs.value = null
        _vpnLiveStats.value = VpnLiveStats()
        _connectedAtElapsedMs.value = null
        _localProxyAddress.value = null
        _tunnelHealth.value = TunnelHealth.UNKNOWN
        _proxyOnlyActive.value = false
    }

    @Suppress("unused")
    private fun connectedLabel(candidate: ProxyCandidate): String =
        if (candidate.countryCode.equals(RECOMMENDED_COUNTRY_CODE, ignoreCase = true)) "Recommended"
        else candidate.countryName.ifBlank { candidate.countryCode }

    @Suppress("unused")
    private val container: AppContainer
        get() = app
}
