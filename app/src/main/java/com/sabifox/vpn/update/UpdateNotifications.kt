package com.sabifox.vpn.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sabifox.vpn.MainActivity
import com.sabifox.vpn.R

internal object UpdateNotifications {
    private const val CHANNEL_ID = "sabifox_updates"
    private const val ID_CONFIRM = 7101
    private const val ID_RESULT = 7102

    fun confirm(context: Context, version: String, confirmIntent: Intent) {
        val pending =
            PendingIntent.getActivity(
                context,
                ID_CONFIRM,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        post(
            context,
            ID_CONFIRM,
            context.getString(R.string.update_notif_confirm_title, version),
            context.getString(R.string.update_notif_confirm_text),
            pending,
        )
    }

    fun failed(context: Context, version: String, message: String) {
        post(
            context,
            ID_RESULT,
            context.getString(R.string.update_notif_failed_title, version),
            message,
            openApp(context),
        )
    }

    fun updated(context: Context, version: String) {
        post(
            context,
            ID_RESULT,
            context.getString(R.string.update_notif_done_title, version),
            context.getString(R.string.update_notif_done_text),
            openApp(context),
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            ID_RESULT,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
        manager.notify(id, notification)
    }
}
