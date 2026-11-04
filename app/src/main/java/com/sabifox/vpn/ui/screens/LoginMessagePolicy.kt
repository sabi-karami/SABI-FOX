package com.sabifox.vpn.ui.screens

/** Validate the native WebView-provided origin, never a value supplied by JavaScript. */
internal object LoginMessagePolicy {
    const val MAX_MESSAGE_CHARS = 65536
    fun trustedOrigin(scheme: String?, host: String?, port: Int, mainFrame: Boolean, size: Int): Boolean =
        mainFrame && size in 0..MAX_MESSAGE_CHARS &&
            scheme.equals("https", ignoreCase = true) &&
            host.equals("accounts.firefox.com", ignoreCase = true) &&
            (port == -1 || port == 443)
}
