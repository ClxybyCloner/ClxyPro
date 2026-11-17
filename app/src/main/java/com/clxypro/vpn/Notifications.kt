package com.clxypro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build

object Notifications {
    const val CHANNEL_ID = "tunnel"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, Texts.appName, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun build(context: Context, title: String, text: String): Notification {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.clxy_logo)
        }.getOrNull()

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return builder.build()
    }
}
