package com.sabifox.vpn.vpn

import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.TunnelSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the configuration produced by the shared SingBoxConfigBuilder into its Windows form:
 * per-app split tunneling becomes process rules (Android uses package lists in the TUN
 * inbound), SABI-FOX's own control-plane traffic bypasses the tunnel (Android "protects" those
 * sockets), and proxy-only mode can switch the Windows system proxy on.
 */
internal object WindowsConfigAdapter {

    fun adapt(
        config: String,
        settings: TunnelSettings,
        setSystemProxy: Boolean,
        ownExecutable: String?,
    ): String {
        val root = JSONObject(config)
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
        var hasTun = false
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(i)
            when (inbound.optString("type")) {
                "tun" -> {
                    hasTun = true
                    inbound.remove("include_package")
                    inbound.remove("exclude_package")
                    inbound.remove("platform")
                    inbound.put("interface_name", "SABI-FOX")
                    inbound.put("stack", "system")
                }
                "mixed" -> if (setSystemProxy && settings.proxyOnlyMode) inbound.put("set_system_proxy", true)
            }
        }

        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray()
        val extra = mutableListOf<JSONObject>()

        if (hasTun) {
            if (!ownExecutable.isNullOrBlank()) {
                extra += JSONObject().put("process_path_regex", JSONArray().put(exactPath(ownExecutable))).put("outbound", "direct-out")
            }
            when (settings.splitTunnelMode) {
                SettingsStore.SplitTunnelMode.INCLUDE -> {
                    val included = settings.includedApps.filter { it.isNotBlank() }
                    require(included.isNotEmpty()) { "Select at least one program to include in the VPN" }
                    extra += JSONObject().put("process_path_regex", JSONArray(included.sorted().map(::exactPath))).put("outbound", "proxy-out")
                    // Everything else leaves through the normal connection.
                    extra += JSONObject().put("network", JSONArray().put("tcp").put("udp")).put("outbound", "direct-out")
                }
                else -> {
                    val excluded = settings.excludedApps.filter { it.isNotBlank() }
                    if (excluded.isNotEmpty()) {
                        extra += JSONObject().put("process_path_regex", JSONArray(excluded.sorted().map(::exactPath))).put("outbound", "direct-out")
                    }
                }
            }
            route.put("find_process", true)
        }

        if (extra.isNotEmpty()) {
            // Keep DNS hijacking and the safety rejects first, then the process rules, then the rest.
            val merged = JSONArray()
            var inserted = false
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                val isSafetyRule = rule.optString("action") in setOf("hijack-dns", "reject")
                if (!inserted && !isSafetyRule) {
                    extra.forEach { merged.put(it) }
                    inserted = true
                }
                merged.put(rule)
            }
            if (!inserted) extra.forEach { merged.put(it) }
            route.put("rules", merged)
        }
        return root.toString()
    }

    /** Case-insensitive, anchored regex that matches exactly one Windows path. */
    private fun exactPath(path: String): String = "(?i)^" + Regex.escape(path.trim()) + "$"
}
