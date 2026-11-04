package com.sabifox.vpn.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.VpnService
import android.os.Build
import androidx.core.content.IntentCompat
import com.sabifox.vpn.BuildConfig
import com.sabifox.vpn.SabiFoxApp
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.vpn.SabiFoxService

private const val TAG = "UpdateInstall"
private const val EXTRA_VERSION = "com.sabifox.vpn.extra.UPDATE_VERSION"

/** Receives PackageInstaller results and the post-update MY_PACKAGE_REPLACED broadcast. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL_STATUS -> onInstallStatus(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced(context)
        }
    }

    private fun onInstallStatus(context: Context, intent: Intent) {
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        val status =
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                        ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched =
                    AppVisibility.foreground &&
                        runCatching { context.startActivity(confirm) }.isSuccess
                if (!launched) UpdateNotifications.confirm(context, version, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> AppLogger.i(TAG, "update $version installed")
            else -> {
                context
                    .getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_RECONNECT_AFTER_UPDATE)
                    .apply()
                val message =
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                AppLogger.w(TAG, "update $version failed: $message")
                UpdateNotifications.failed(context, version, message)
            }
        }
    }

    private fun onPackageReplaced(context: Context) {
        val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val reconnect = prefs.getBoolean(KEY_RECONNECT_AFTER_UPDATE, false)
        prefs.edit().remove(KEY_RECONNECT_AFTER_UPDATE).apply()
        val app = context.applicationContext as? SabiFoxApp
        if (app != null) AutoUpdateScheduler.sync(context, app.appUpdater.autoInstall)
        if (reconnect) {
            val proxyOnly = app?.settingsStore?.proxyOnlyMode == true
            if (proxyOnly || VpnService.prepare(context) == null) {
                runCatching { SabiFoxService.start(context) }
                    .onFailure { AppLogger.w(TAG, "could not reconnect after update", it) }
            }
        }
        UpdateNotifications.updated(context, BuildConfig.VERSION_NAME)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.sabifox.vpn.action.UPDATE_INSTALL_STATUS"

        fun statusIntentSender(context: Context, sessionId: Int, version: String): IntentSender {
            val intent =
                Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_VERSION, version)
            val mutability =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutability
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }
    }
}

/** Tracks whether an activity is visible, so the installer prompt can open directly. */
object AppVisibility {
    @Volatile var foreground: Boolean = false
}
