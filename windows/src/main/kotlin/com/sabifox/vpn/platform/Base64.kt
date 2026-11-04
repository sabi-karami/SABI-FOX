package com.sabifox.vpn.platform

/** android.util.Base64 compatible helpers backed by java.util.Base64. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    fun encodeToString(input: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        val text = encoder.encodeToString(input)
        if (flags and NO_WRAP != 0) return text
        val sep = if (flags and CRLF != 0) "\r\n" else "\n"
        return text.chunked(76).joinToString(sep, postfix = sep)
    }

    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray(Charsets.US_ASCII)

    fun decode(str: String, flags: Int): ByteArray {
        val clean = str.filterNot { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
        val normalized =
            if (flags and URL_SAFE != 0) clean.replace('-', '+').replace('_', '/')
            else clean.replace('-', '+').replace('_', '/')
        val padded = normalized.trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("bad base-64", error)
        }
    }

    fun decode(input: ByteArray, flags: Int): ByteArray = decode(String(input, Charsets.US_ASCII), flags)
}
