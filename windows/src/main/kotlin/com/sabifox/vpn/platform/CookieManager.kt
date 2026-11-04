package com.sabifox.vpn.platform

import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie hand-off between the sign-in window and the HTTP client, mirroring the slice of
 * android.webkit.CookieManager SABI-FOX uses. The sign-in helper reports the cookies of the
 * Mozilla account pages here (for example the bot-protection clearance cookie).
 */
class CookieManager private constructor() {
    private val cookies = ConcurrentHashMap<String, LinkedHashMap<String, String>>()

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()

    /** Returns "name=value; name2=value2" for every cookie whose domain matches the url host. */
    fun getCookie(url: String): String? {
        val host = hostOf(url)
        if (host.isEmpty()) return null
        val parts = mutableListOf<String>()
        for ((domain, jar) in cookies) {
            val d = domain.trimStart('.')
            if (host == d || host.endsWith(".$d")) {
                synchronized(jar) { jar.forEach { (k, v) -> parts.add("$k=$v") } }
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /** Accepts a Set-Cookie style string. */
    fun setCookie(url: String, value: String) {
        val attributes = value.split(';').map { it.trim() }
        val first = attributes.firstOrNull() ?: return
        val eq = first.indexOf('=')
        if (eq <= 0) return
        val domain =
            attributes.drop(1).firstOrNull { it.startsWith("domain=", ignoreCase = true) }
                ?.substringAfter('=')?.lowercase()?.trimStart('.')
                ?: hostOf(url)
        if (domain.isEmpty()) return
        val jar = cookies.computeIfAbsent(domain) { LinkedHashMap() }
        synchronized(jar) { jar[first.substring(0, eq).trim()] = first.substring(eq + 1).trim() }
    }

    /** Every stored cookie as (domain, name, value), used to seed the sign-in window. */
    fun entries(): List<Triple<String, String, String>> =
        cookies.flatMap { (domain, jar) -> synchronized(jar) { jar.map { (k, v) -> Triple(domain, k, v) } } }

    fun setAcceptCookie(@Suppress("UNUSED_PARAMETER") accept: Boolean) {}

    fun flush() {}

    fun removeAllCookies(@Suppress("UNUSED_PARAMETER") callback: ((Boolean) -> Unit)?) {
        cookies.clear()
    }

    companion object {
        private val instance = CookieManager()

        fun getInstance(): CookieManager = instance
    }
}
