package com.sabifox.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.widget.RemoteViews
import com.sabifox.vpn.SabiFoxApp
import com.sabifox.vpn.MainActivity
import com.sabifox.vpn.R
import com.sabifox.vpn.data.SettingsStore
import com.sabifox.vpn.data.ThemeMode
import com.sabifox.vpn.data.TokenStore
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.vpn.SabiFoxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickConnectWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, SabiFoxService.state.value)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_VPN) {
            if (SabiFoxService.state.value != ConnectionState.DISCONNECTED) {
                SabiFoxService.stop(context)
                return
            }
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? SabiFoxApp
                    val tokenStore = app?.tokenStore ?: TokenStore(context)
                    val signedIn = tokenStore.hasStoredSession()
                    withContext(Dispatchers.Main) { handleToggle(context, signedIn) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    com.sabifox.vpn.data.AppLogger.w(
                        "QuickConnectWidget",
                        "Quick connect failed",
                        error,
                    )
                    withContext(Dispatchers.Main) { launchMainActivity(context) }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun handleToggle(context: Context, signedIn: Boolean) {
        when (SabiFoxService.state.value) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING -> return
            ConnectionState.DISCONNECTED -> {
                val app = context.applicationContext as? SabiFoxApp
                if (!signedIn) {
                    launchMainActivity(context)
                    return
                }

                val settingsStore = app?.settingsStore ?: SettingsStore(context)
                if (settingsStore.proxyOnlyMode) {
                    SabiFoxService.start(context)
                    return
                }

                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    launchMainActivity(context)
                } else {
                    SabiFoxService.start(context)
                }
            }
        }
    }

    private fun launchMainActivity(context: Context) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        context.startActivity(intent)
    }

    companion object {
        const val ACTION_TOGGLE_VPN = "com.sabifox.vpn.action.TOGGLE_VPN"

        fun updateAll(context: Context, state: ConnectionState) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, QuickConnectWidgetProvider::class.java)
            val appWidgetIds =
                runCatching { appWidgetManager.getAppWidgetIds(componentName) }.getOrNull()
                    ?: return
            if (appWidgetIds.isEmpty()) return
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, state)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: ConnectionState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)

            val toggleIntent =
                Intent(context, QuickConnectWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_VPN
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            val settingsStore =
                (context.applicationContext as? SabiFoxApp)?.settingsStore ?: SettingsStore(context)
            val systemDark =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val isDark =
                when (settingsStore.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

            val bgRes =
                when (state) {
                    ConnectionState.CONNECTED,
                    ConnectionState.DISCONNECTED ->
                        if (isDark) R.drawable.widget_bg_disconnected_dark
                        else R.drawable.widget_bg_disconnected_light
                    ConnectionState.CONNECTING ->
                        if (isDark) R.drawable.widget_bg_connecting_dark
                        else R.drawable.widget_bg_connecting_light
                }

            val iconColor =
                when (state) {
                    ConnectionState.CONNECTED ->
                        if (isDark) 0xFF7BDF92.toInt() else 0xFF1B6E2F.toInt()
                    ConnectionState.CONNECTING ->
                        if (isDark) 0xFFFFC46B.toInt() else 0xFF8A5A00.toInt()
                    ConnectionState.DISCONNECTED ->
                        if (isDark) 0xFFE9E0E4.toInt() else 0xFF1E1A1D.toInt()
                }

            views.setImageViewResource(R.id.widget_background, bgRes)
            views.setImageViewResource(R.id.widget_button, R.drawable.ic_power)
            views.setInt(R.id.widget_button, "setColorFilter", iconColor)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
