package com.sabifox.vpn.platform.windows

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt

/** Windows Data Protection API: encrypts secrets so only the current Windows user can read them. */
object Dpapi {
    private fun entropy(purpose: String): ByteArray = "SABI-FOX/$purpose".toByteArray(Charsets.UTF_8)

    fun protect(data: ByteArray, purpose: String): ByteArray =
        Crypt32Util.cryptProtectData(data, entropy(purpose), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "SABI-FOX", null)

    fun unprotect(data: ByteArray, purpose: String): ByteArray =
        Crypt32Util.cryptUnprotectData(data, entropy(purpose), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
}
