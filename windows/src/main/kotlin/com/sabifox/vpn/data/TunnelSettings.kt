package com.sabifox.vpn.data

data class TunnelSettings(
    val automaticRecovery: Boolean,
    val dnsProvider: SettingsStore.DnsProvider,
    val effectiveCustomEdgeAddress: String?,
    val effectiveDohUrl: String,
    val excludedApps: Set<String>,
    val includedApps: Set<String>,
    val splitTunnelMode: SettingsStore.SplitTunnelMode,
    val fragmentFallbackDelay: Int,
    val httpVersion: SettingsStore.HttpVersion,
    val ipv6Enabled: Boolean,
    val maxConcurrentStreams: Int,
    val mtu: Int,
    val localProxyAuthEnabled: Boolean,
    val localProxyUsername: String,
    val localProxyPassword: String,
    val proxyOnlyMode: Boolean,
    val socksBindAddress: String,
    val socksPort: Int,
    val tcpFastOpen: Boolean,
    val tlsFragmentMode: SettingsStore.TlsFragmentMode,
    val upstreamProxyEnabled: Boolean,
    val upstreamProxyHost: String,
    val upstreamProxyPassword: String,
    val upstreamProxyPort: Int,
    val upstreamProxyType: SettingsStore.UpstreamProxyType,
    val upstreamProxyUsername: String,
    val utlsFingerprint: SettingsStore.UtlsFingerprint,
    val bypassLocalNetwork: Boolean = true,
) {
    override fun toString() =
        "TunnelSettings(proxyOnlyMode=$proxyOnlyMode, httpVersion=$httpVersion)"
}

private val sessionRandom = java.security.SecureRandom()

private fun randomToken(bytes: Int): String {
    val buffer = ByteArray(bytes)
    sessionRandom.nextBytes(buffer)
    return buffer.joinToString("") { "%02x".format(it) }
}

/**
 * In full-VPN mode the local listener is only used internally (latency and exit-IP checks), so it
 * is protected with random per-connection credentials. Otherwise any app on the device - including
 * apps excluded through split tunneling - could use or detect the tunnel through 127.0.0.1.
 */
private fun SettingsStore.effectiveLocalProxyCredentials(): Triple<Boolean, String, String> =
    when {
        localProxyAuthEnabled -> Triple(true, localProxyUsername, localProxyPassword)
        !proxyOnlyMode -> Triple(true, "sf" + randomToken(8), randomToken(24))
        else -> Triple(false, "", "")
    }

fun SettingsStore.tunnelSnapshot(): TunnelSettings =
    synchronized(proxySettingsLock) {
        val localAuth = effectiveLocalProxyCredentials()
        require(!upstreamProxyEnabled || !upstreamReviewRequired) {
            "Upstream TLS has been removed. Review the upstream proxy settings before connecting."
        }
        TunnelSettings(
            automaticRecovery = automaticRecovery,
            bypassLocalNetwork = bypassLocalNetwork,
            dnsProvider = dnsProvider,
            effectiveCustomEdgeAddress = effectiveCustomEdgeAddress,
            effectiveDohUrl = effectiveDohUrl,
            excludedApps = excludedApps.toSet(),
            includedApps = includedApps.toSet(),
            splitTunnelMode = splitTunnelMode,
            fragmentFallbackDelay = fragmentFallbackDelay,
            httpVersion = httpVersion,
            ipv6Enabled = ipv6Enabled,
            maxConcurrentStreams = maxConcurrentStreams,
            mtu = mtu,
            localProxyAuthEnabled = localAuth.first,
            localProxyUsername = localAuth.second,
            localProxyPassword = localAuth.third,
            proxyOnlyMode = proxyOnlyMode,
            socksBindAddress = socksBindAddress,
            socksPort = socksPort,
            tcpFastOpen = tcpFastOpen,
            tlsFragmentMode = tlsFragmentMode,
            upstreamProxyEnabled = upstreamProxyEnabled,
            upstreamProxyHost = if (upstreamProxyEnabled) upstreamProxyHost else "",
            upstreamProxyPassword = if (upstreamProxyEnabled) upstreamProxyPassword else "",
            upstreamProxyPort =
                if (upstreamProxyEnabled) upstreamProxyPort
                else SettingsStore.DEFAULT_UPSTREAM_PROXY_PORT,
            upstreamProxyType =
                if (upstreamProxyEnabled) upstreamProxyType
                else SettingsStore.UpstreamProxyType.SOCKS5,
            upstreamProxyUsername = if (upstreamProxyEnabled) upstreamProxyUsername else "",
            utlsFingerprint = utlsFingerprint,
        )
    }
