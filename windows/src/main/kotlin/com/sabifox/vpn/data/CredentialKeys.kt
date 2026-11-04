package com.sabifox.vpn.data

import com.sabifox.vpn.platform.AppPaths
import com.sabifox.vpn.platform.windows.Dpapi
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Windows replacement for the Android Keystore key. Each alias gets a random AES-256 key that
 * is stored only in DPAPI-protected form, bound to the signed-in Windows user, so the encrypted
 * tokens cannot be read by other users or copied to another PC.
 */
internal object CredentialKeys {
    private val lock = Any()
    private val cache = HashMap<String, SecretKey>()

    private fun keyFile(alias: String): File {
        val safe = alias.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(AppPaths.root, "keys").apply { mkdirs() }, "$safe.key")
    }

    fun get(alias: String, create: Boolean): SecretKey =
        synchronized(lock) {
            cache[alias]?.let { return@synchronized it }
            val file = keyFile(alias)
            if (file.isFile) {
                val raw = Dpapi.unprotect(file.readBytes(), alias)
                check(raw.size == 32) { "Unexpected credential key size" }
                return@synchronized SecretKeySpec(raw, "AES").also { cache[alias] = it }
            }
            check(create) { "The credential encryption key is unavailable" }
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val protected = Dpapi.protect(raw, alias)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(protected)
            check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) {
                "Could not store the credential encryption key"
            }
            SecretKeySpec(raw, "AES").also { cache[alias] = it }
        }
}
