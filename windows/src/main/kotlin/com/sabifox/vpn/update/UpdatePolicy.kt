package com.sabifox.vpn.update

import com.sabifox.vpn.data.AppLinks
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import org.json.JSONObject

internal data class UpdateAsset(val name: String, val url: String, val size: Long, val digest: String?)

internal data class AppRelease(
    val tag: String,
    val asset: UpdateAsset,
    val checksums: UpdateAsset,
    val signature: UpdateAsset,
)

internal const val WINDOWS_SETUP_ASSET = "SABI-FOX-Windows-Setup-x64.exe"
internal const val WINDOWS_PORTABLE_ASSET = "SABI-FOX-Windows-Portable-x64.zip"
internal const val WINDOWS_CHECKSUMS_ASSET = "SHA256SUMS-windows.txt"
internal const val WINDOWS_SIGNATURE_ASSET = "SHA256SUMS-windows.txt.sig"
internal const val MAX_UPDATE_BYTES = 600L * 1024 * 1024

/** Ed25519 key that signs SHA256SUMS-windows.txt in the release workflow. */
private const val UPDATE_PUBLIC_KEY = "MCowBQYDK2VwAyEA8/1hRra4FNCKCe6VSmuuGVq3SLTjbTfI/ecxFdMbnz4="

private fun releaseTagComparison(tag: String, currentVersionName: String): Int? {
    fun parts(value: String): List<Long>? {
        val segments = value.removePrefix("v").split('.')
        if (segments.size !in 2..4 || segments.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) return null
        return segments.map { it.toLongOrNull() ?: return null }
    }
    val release = parts(tag) ?: return null
    val current = parts(currentVersionName) ?: return null
    for (index in 0 until maxOf(release.size, current.size)) {
        val comparison = release.getOrElse(index) { 0L }.compareTo(current.getOrElse(index) { 0L })
        if (comparison != 0) return comparison
    }
    return 0
}

/** True when [tag] is strictly newer than the running version. */
internal fun isNewerRelease(tag: String, currentVersionName: String): Boolean =
    releaseTagComparison(tag, currentVersionName)?.let { it > 0 } ?: false

/**
 * Picks this build's asset (installer or portable zip) from a GitHub release. Returns null when
 * the release is not newer or carries no Windows build yet (for example an Android-only release).
 */
internal fun parseWindowsRelease(json: JSONObject, currentVersionName: String, portable: Boolean): AppRelease? {
    check(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "No stable release is available" }
    val tag = json.getString("tag_name")
    if (!isNewerRelease(tag, currentVersionName)) return null
    val assets = json.getJSONArray("assets")
    val parsed =
        (0 until assets.length()).map { index ->
            val asset = assets.getJSONObject(index)
            UpdateAsset(
                asset.getString("name"),
                asset.getString("browser_download_url"),
                asset.getLong("size"),
                asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            )
        }.filter { trustedAssetUrl(it.url) }
    val wanted = if (portable) WINDOWS_PORTABLE_ASSET else WINDOWS_SETUP_ASSET
    val asset = parsed.singleOrNull { it.name == wanted && it.size in 1..MAX_UPDATE_BYTES } ?: return null
    val sums = parsed.singleOrNull { it.name == WINDOWS_CHECKSUMS_ASSET } ?: return null
    val sig = parsed.singleOrNull { it.name == WINDOWS_SIGNATURE_ASSET } ?: return null
    return AppRelease(tag, asset, sums, sig)
}

internal fun trustedAssetUrl(value: String): Boolean =
    runCatching {
            val uri = java.net.URI(value)
            uri.scheme == "https" &&
                uri.host == "github.com" &&
                uri.rawUserInfo == null &&
                uri.rawFragment == null &&
                uri.rawQuery == null &&
                uri.port == -1 &&
                uri.rawPath?.startsWith(AppLinks.RELEASE_DOWNLOAD_PATH) == true
        }
        .getOrDefault(false)

/** Verifies the release workflow's Ed25519 signature over the checksum file. */
internal fun verifyChecksumSignature(checksums: ByteArray, signature: ByteArray): Boolean =
    runCatching {
            val key =
                KeyFactory.getInstance("Ed25519")
                    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(UPDATE_PUBLIC_KEY)))
            Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(checksums)
                verify(signature)
            }
        }
        .getOrDefault(false)

/** Reads the hash for [assetName] from a `sha256sum` style file. */
internal fun checksumFor(sums: String, assetName: String): String? =
    sums.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[1].trimStart('*').trim()
            if (name == assetName) parts[0].lowercase(Locale.ROOT) else null
        }
        .singleOrNull()
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
