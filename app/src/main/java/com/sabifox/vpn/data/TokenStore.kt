package com.sabifox.vpn.data

import android.content.Context
import com.sabifox.vpn.data.model.RuntimeAuth

private const val TAG = "TokenStore"

class TokenStore(context: Context) {

    private val mutableStorageError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val storageError: kotlinx.coroutines.flow.StateFlow<String?> = mutableStorageError
    private val appContext = context.applicationContext
    private val legacyPrefs by lazy { SecurePreferences(appContext, "sabifox_tokens", ::storageFailure) }
    private val vault by lazy { SecurePreferences(appContext, "sabifox_accounts_v1", ::storageFailure) }

    data class AccountSlot(
        val slot: Int,
        val email: String?,
        val signedIn: Boolean,
        val remainingBytes: Long?,
        val maximumBytes: Long?,
        val quotaCheckedAt: Long?,
    )

    private fun key(slot: Int, name: String) = "slot_${slot}_$name"

    // Import the previous single-account session once. Preserve it if it has no verified email;
    // do not claim it is any particular account. One atomic encrypted write includes the marker.
    private fun ensureVault() {
        if (vault.getString("schema", null) == "1") {
            // Once safely migrated, remove the old encrypted copy as well.
            if (legacyPrefs.getString(KEY_ACCESS_TOKEN, null) != null) legacyPrefs.edit().clear().commit()
            return
        }
        val legacyAccess = legacyPrefs.getString(KEY_ACCESS_TOKEN, null)
        val editor = vault.edit().putString("schema", "1").putLong("active_slot", 0L)
        if (!legacyAccess.isNullOrBlank()) {
            editor.putString(key(0, KEY_ACCESS_TOKEN), legacyAccess)
                .putString(key(0, KEY_REFRESH_TOKEN), legacyPrefs.getString(KEY_REFRESH_TOKEN, null))
                .putLong(key(0, KEY_EXPIRES_AT), legacyPrefs.getLong(KEY_EXPIRES_AT, 0L))
        }
        editor.commit()
        if (!legacyAccess.isNullOrBlank()) legacyPrefs.edit().clear().commit()
    }

    private fun activeSlotLocked(): Int {
        ensureVault()
        return vault.getLong("active_slot", 0L).toInt().also { check(it in 0..1) }
    }

    val activeSlot: Int
        get() = synchronized(sessionLock) { activeSlotLocked() }

    fun listAccounts(): List<AccountSlot> = synchronized(sessionLock) {
        ensureVault()
        (0..1).map { slot ->
            AccountSlot(
                slot = slot,
                email = vault.getString(key(slot, "email"), null),
                signedIn = !vault.getString(key(slot, KEY_ACCESS_TOKEN), null).isNullOrBlank(),
                remainingBytes = vault.getLong(key(slot, "quota_remaining"), -1L).takeIf { it >= 0 },
                maximumBytes = vault.getLong(key(slot, "quota_max"), -1L).takeIf { it >= 0 },
                quotaCheckedAt = vault.getLong(key(slot, "quota_checked_at"), 0L).takeIf { it > 0 },
            )
        }
    }

    fun selectAccount(slot: Int) {
        require(slot in 0..1)
        val listener = synchronized(sessionLock) {
            if (activeSlotLocked() == slot) return
            vault.edit().putLong("active_slot", slot.toLong()).commit()
            epoch++
            onSessionInvalidated
        }
        listener?.invoke()
    }

    fun setVerifiedEmailIfCurrent(expectedEpoch: Long, email: String) = synchronized(sessionLock) {
        check(epoch == expectedEpoch && hasStoredSession()) { "Session changed" }
        val slot = activeSlotLocked()
        val otherEmail = vault.getString(key(1 - slot, "email"), null)
        check(!email.equals(otherEmail, ignoreCase = true)) { "Account already in the other slot" }
        vault.edit().putString(key(slot, "email"), email).commit()
    }

    fun saveQuotaIfCurrent(expectedEpoch: Long, remaining: Long?, maximum: Long?) =
        synchronized(sessionLock) {
            check(epoch == expectedEpoch && hasStoredSession()) { "Session changed" }
            val slot = activeSlotLocked()
            vault.edit()
                .putLong(key(slot, "quota_remaining"), remaining ?: -1L)
                .putLong(key(slot, "quota_max"), maximum ?: -1L)
                .putLong(key(slot, "quota_checked_at"), System.currentTimeMillis())
                .commit()
        }


    private fun storageFailure(error: Exception): SecureStorageException {
        val failure = error as? SecureStorageException ?: SecureStorageException(error)
        mutableStorageError.value = failure.message
        return failure
    }

    val sessionEpoch: Long
        get() = epoch

    private val invalidationListener =
        java.util.concurrent.atomic.AtomicReference<(() -> Unit)?>(null)
    var onSessionInvalidated: (() -> Unit)?
        get() = invalidationListener.get()
        set(value) {
            invalidationListener.set(value)
        }

    fun clearSessionInvalidationListener(expected: () -> Unit) {
        invalidationListener.compareAndSet(expected, null)
    }

    fun <T> withCurrentSession(expectedEpoch: Long, block: () -> T): T =
        synchronized(sessionLock) {
            check(epoch == expectedEpoch && hasStoredSession()) { "Session changed" }
            block()
        }

    fun saveAuthIfCurrent(auth: RuntimeAuth, expectedEpoch: Long) =
        synchronized(sessionLock) {
            check(epoch == expectedEpoch) {
                "Session changed while credentials were being refreshed"
            }
            saveAuth(auth)
        }

    private fun saveAuth(auth: RuntimeAuth) =
        synchronized(sessionLock) {
            val slot = activeSlotLocked()
            vault.edit()
                .putString(key(slot, KEY_ACCESS_TOKEN), auth.accessToken)
                .putString(key(slot, KEY_REFRESH_TOKEN), auth.refreshToken)
                .putLong(key(slot, KEY_EXPIRES_AT), auth.expiresAtEpochSeconds)
                .commit()
        }

    fun loadAuth(): RuntimeAuth? =
        synchronized(sessionLock) {
            runCatching {
                    val slot = activeSlotLocked()
                    val access = vault.getString(key(slot, KEY_ACCESS_TOKEN), null)?.takeIf { it.isNotBlank() }
                    mutableStorageError.value = null
                    if (access == null) return@runCatching null
                    val refresh =
                        vault.getString(key(slot, KEY_REFRESH_TOKEN), null)?.takeIf { it.isNotBlank() }
                    val expiresAt = vault.getLong(key(slot, KEY_EXPIRES_AT), 0L)
                    mutableStorageError.value = null
                    RuntimeAuth(access, refresh, expiresAt)
                }
                .onFailure {
                    storageFailure(
                        it as? Exception ?: IllegalStateException("Could not read credentials", it)
                    )
                    AppLogger.w(TAG, "could not read the stored session", it)
                }
                .getOrNull()
        }

    fun hasValidAccessToken(): Boolean {
        val auth = loadAuth() ?: return false
        if (auth.expiresAtEpochSeconds <= 0L) return false
        val nowSeconds = System.currentTimeMillis() / 1000
        return auth.expiresAtEpochSeconds - nowSeconds > CLOCK_SKEW_TOLERANCE_SECONDS
    }

    fun hasStoredSession(): Boolean = loadAuth() != null

    fun clear() {
        clearIfCurrent(null)
    }

    fun clearIfCurrent(expectedEpoch: Long?): Boolean {
        val listener =
            synchronized(sessionLock) {
                if (expectedEpoch != null && epoch != expectedEpoch) return false
                val slot = activeSlotLocked()
                vault.edit()
                    .remove(key(slot, KEY_ACCESS_TOKEN))
                    .remove(key(slot, KEY_REFRESH_TOKEN))
                    .remove(key(slot, KEY_EXPIRES_AT))
                    .remove(key(slot, "email"))
                    .remove(key(slot, "quota_remaining"))
                    .remove(key(slot, "quota_max"))
                    .remove(key(slot, "quota_checked_at"))
                    .commit()
                epoch++
                onSessionInvalidated
            }
        listener?.invoke()
        return true
    }

    fun completeLoginIfCurrent(auth: RuntimeAuth, expectedEpoch: Long, verifiedEmail: String) {
        synchronized(sessionLock) {
            check(epoch == expectedEpoch) { "Sign-in was superseded" }
            val slot = activeSlotLocked()
            val otherEmail = vault.getString(key(1 - slot, "email"), null)
            check(!verifiedEmail.equals(otherEmail, ignoreCase = true)) {
                "This Firefox account is already in the other slot."
            }
            vault.edit()
                .putString(key(slot, KEY_ACCESS_TOKEN), auth.accessToken)
                .putString(key(slot, KEY_REFRESH_TOKEN), auth.refreshToken)
                .putLong(key(slot, KEY_EXPIRES_AT), auth.expiresAtEpochSeconds)
                .putString(key(slot, "email"), verifiedEmail)
                .remove(key(slot, "quota_remaining"))
                .remove(key(slot, "quota_max"))
                .remove(key(slot, "quota_checked_at"))
                .commit()
            epoch++
        }
    }

    companion object {
        private val sessionLock = Any()
        @Volatile private var epoch = 0L
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        private const val CLOCK_SKEW_TOLERANCE_SECONDS = 60L
    }
}
