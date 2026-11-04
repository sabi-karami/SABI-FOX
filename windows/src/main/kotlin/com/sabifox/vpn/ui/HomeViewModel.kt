package com.sabifox.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.FxaAuthRepository
import com.sabifox.vpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.sabifox.vpn.data.GuardianClient
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.vpn.ExitCheck
import com.sabifox.vpn.vpn.SabiFoxService
import java.net.NetworkInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val settingsStore: SettingsStore,
    private val guardianClient: GuardianClient,
    private val authRepository: FxaAuthRepository,
) : ViewModel() {
    val account = settingsStore.account
    private var addressJob: Job? = null
    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    init {
        refreshAddress()
        refreshAccount()
        watchNetworkChanges()
    }

    /**
     * Windows has no per-app network callback like Android's ConnectivityManager, so the
     * physical adapters are sampled instead; a change (new Wi-Fi, cable, hotspot) refreshes the
     * direct address and the account quota just like the Android app does.
     */
    private fun watchNetworkChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            var last = networkFingerprint()
            while (isActive) {
                delay(10_000L)
                val now = networkFingerprint()
                if (now != last) {
                    last = now
                    refreshAddress()
                    refreshAccount()
                }
            }
        }
    }

    private fun networkFingerprint(): Set<String> =
        runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .filterNot { it.name.contains("SABI-FOX", true) || it.displayName.orEmpty().contains("SABI-FOX", true) || it.displayName.orEmpty().contains("Wintun", true) }
                    .flatMap { nic -> nic.inetAddresses.toList().map { "${nic.name}/${it.hostAddress}" } }
                    .toSet()
            }
            .getOrDefault(emptySet())

    fun refreshAccount() {
        viewModelScope.launch {
            try {
                val token = authRepository.currentAccessToken() ?: return@launch
                val info = guardianClient.fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token)
                val hasKnownQuota = info.quotaRemaining != null || info.maxBytes != null
                val isUnlimited = !info.limitedBandwidth && !hasKnownQuota
                withContext(Dispatchers.IO) {
                    settingsStore.saveAccountQuota(isUnlimited, info.quotaRemaining, info.maxBytes)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.w("HomeViewModel", "Account refresh failed; keeping last known quota", e)
            }
        }
    }

    fun triggerPing() {
        if (_isPinging.value) return
        viewModelScope.launch {
            _isPinging.value = true
            try {
                SabiFoxService.pingNow()
            } finally {
                _isPinging.value = false
            }
        }
    }

    private fun refreshAddress() {
        addressJob?.cancel()
        addressJob =
            viewModelScope.launch {
                delay(750L)
                ExitCheck().fetchDirectIpInfo().onSuccess { info ->
                    runCatching {
                            withContext(Dispatchers.IO) { settingsStore.saveDirectAddress(info.ip, info.countryCode) }
                        }
                        .onFailure { if (it is CancellationException) throw it }
                }
            }
    }

    companion object {
        fun factory(
            settingsStore: SettingsStore,
            guardianClient: GuardianClient,
            authRepository: FxaAuthRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(settingsStore, guardianClient, authRepository) }
        }
    }
}
