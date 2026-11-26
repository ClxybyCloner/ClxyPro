package com.clxypro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.service.NotificationActionReceiver
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object Notifications {
    const val CHANNEL_ID = "tunnel"
    const val NOTIFICATION_ID = 1001
    const val UPDATE_CHANNEL_ID = "updates"
    const val UPDATE_NOTIFICATION_ID = 1002

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
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)

        // Disconnect straight from the notification shade: the action is
        // part of build() itself, so it survives the speed updater's
        // once-a-second rebuilds. The receiver stops whichever service
        // is running (VPN or proxy).
        val disconnectIntent = Intent(NotificationActionReceiver.ACTION_DISCONNECT).apply {
            setPackage(context.packageName)
        }
        val disconnectPending = PendingIntent.getBroadcast(
            context,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            Notification.Action.Builder(null, Texts.disconnect, disconnectPending).build()
        )

        squareLogo(context)?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    // Cached square logo: build() runs every second while the speed updater
    // ticks, so the bitmap is made once per process, not decoded per update.
    @Volatile
    private var logoCache: Bitmap? = null
    private var logoFailed = false

    /**
     * clxy_logo is 240x180 (4:3): used raw as the large icon, the
     * notification's square/circle mask center-crops it and the mark comes
     * out half-cut. Letterbox it into a 256x256 square at 70% fit so the
     * whole logo survives even a circular mask.
     */
    private fun squareLogo(context: Context): Bitmap? {
        logoCache?.let { return it }
        if (logoFailed) return null
        val src = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.clxy_logo)
        }.getOrNull()
        if (src == null) {
            logoFailed = true
            return null
        }
        val size = 256
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fit = size * 0.7f
        val scale = minOf(fit / src.width, fit / src.height)
        val w = src.width * scale
        val h = src.height * scale
        Canvas(out).drawBitmap(
            src,
            null,
            RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f),
            null
        )
        logoCache = out
        return out
    }

    /**
     * Live up/down speed in the VPN/proxy notification, once a second.
     * The toggle in Profile (Prefs.showSpeedInNotification, default ON)
     * existed long before this updater — the setting was wired to nothing,
     * so the notification always showed the static "VPN is active". The
     * pref is re-read every tick, so flipping the toggle mid-connection
     * takes effect without reconnecting. Sampling matches HomeScreen
     * (whole-device TrafficStats totals, UID fallback), so the numbers in
     * the notification agree with the numbers in the app.
     */
    object SpeedUpdater {
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        @Volatile
        private var future: ScheduledFuture<*>? = null
        @Volatile
        private var lastText: String? = null

        /**
         * Bumped by every start and stop. A tick already in flight when
         * stop() lands must not re-post the notification after teardown
         * removed it (ongoing notifications can't be swiped away).
         */
        private val runId = AtomicLong(0)

        fun start(context: Context, baseText: String) {
            stop()
            val id = runId.incrementAndGet()
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(NotificationManager::class.java)
            var lastRx = sampleRx()
            var lastTx = sampleTx()
            var lastTime = SystemClock.elapsedRealtime()
            future = scheduler.scheduleAtFixedRate({
                if (id != runId.get()) return@scheduleAtFixedRate
                runCatching {
                    val now = SystemClock.elapsedRealtime()
                    val curRx = sampleRx()
                    val curTx = sampleTx()
                    val elapsedSec = ((now - lastTime) / 1000f).coerceAtLeast(0.1f)
                    val rxBytes = (curRx - lastRx).coerceAtLeast(0L)
                    val txBytes = (curTx - lastTx).coerceAtLeast(0L)
                    lastRx = curRx
                    lastTx = curTx
                    lastTime = now
                    val text = if (Prefs.showSpeedInNotification) {
                        val down = (rxBytes / 1000f) / elapsedSec
                        val up = (txBytes / 1000f) / elapsedSec
                        "↓ ${fmt(down)} KB/s ↑ ${fmt(up)} KB/s"
                    } else {
                        baseText
                    }
                    if (id == runId.get() && text != lastText) {
                        lastText = text
                        manager.notify(NOTIFICATION_ID, build(appContext, Texts.appName, text))
                    }
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS)
        }

        fun stop() {
            runId.incrementAndGet()
            future?.cancel(false)
            future = null
            lastText = null
        }

        private fun sampleRx(): Long {
            val total = TrafficStats.getTotalRxBytes()
            if (total > 0L) return total
            val uid = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            return if (uid > 0L) uid else 0L
        }

        private fun sampleTx(): Long {
            val total = TrafficStats.getTotalTxBytes()
            if (total > 0L) return total
            val uid = TrafficStats.getUidTxBytes(android.os.Process.myUid())
            return if (uid > 0L) uid else 0L
        }

        /** Same format as HomeScreen: whole KB/s. */
        private fun fmt(kbps: Float): String =
            String.format(Locale.US, "%.0f", kbps)
    }

    /**
     * A new release is out: one tap opens the app, where the home banner
     * and the Profile entry lead to the update dialog. Own channel at
     * default importance, so it actually alerts — the tunnel channel is
     * low-importance and silent by design.
     */
    fun showUpdate(context: Context, release: com.clxypro.vpn.core.GithubUpdates.Release) {
        ensureUpdateChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("CLXY update available")
            .setContentText("Version ${release.version} is ready — tap to update")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun ensureUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
