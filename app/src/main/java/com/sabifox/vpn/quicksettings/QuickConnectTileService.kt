package com.sabifox.vpn.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sabifox.vpn.SabiFoxApp
import com.sabifox.vpn.MainActivity
import com.sabifox.vpn.R
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.TokenStore
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.vpn.SabiFoxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickConnectTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob =
            serviceScope.launch { SabiFoxService.state.collect { state -> updateTileState(state) } }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (SabiFoxService.state.value) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING -> {
                SabiFoxService.stop(this)
            }
            ConnectionState.DISCONNECTED -> {
                serviceScope.launch {
                    try {
                        val app = applicationContext as? SabiFoxApp
                        val tokenStore = app?.tokenStore ?: TokenStore(this@QuickConnectTileService)
                        if (!withContext(Dispatchers.IO) { tokenStore.hasStoredSession() }) {
                            launchMainActivityAndCollapse()
                            return@launch
                        }

                        if (SabiFoxService.state.value != ConnectionState.DISCONNECTED)
                            return@launch
                        val settingsStore =
                            app?.settingsStore ?: SettingsStore(this@QuickConnectTileService)
                        if (settingsStore.proxyOnlyMode) {
                            SabiFoxService.start(this@QuickConnectTileService)
                            return@launch
                        }

                        val prepareIntent = VpnService.prepare(this@QuickConnectTileService)
                        if (prepareIntent != null) {
                            launchMainActivityAndCollapse()
                        } else {
                            SabiFoxService.start(this@QuickConnectTileService)
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        com.sabifox.vpn.data.AppLogger.w(
                            "QuickConnectTile",
                            "Quick connect failed",
                            error,
                        )
                        launchMainActivityAndCollapse()
                    }
                }
            }
        }
    }

    private fun updateTileState(state: ConnectionState) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_power)
        tile.label = getString(R.string.quick_connect)
        when (state) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.quick_connect_connected)
                }
            }
            ConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.quick_connect_connecting)
                }
            }
            ConnectionState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.quick_connect_disconnected)
                }
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchMainActivityAndCollapse() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        listenJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
