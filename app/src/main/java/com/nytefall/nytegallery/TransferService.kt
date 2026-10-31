package com.nytefall.nytegallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.nytefall.nytegallery.ui.TransferProgress

/**
 * Keeps a user-started vault transfer alive if the app is left with Home.
 * The work itself stays in the process. This service is not restarted after
 * the process dies, so a half-written vault is not run again automatically.
 */
class TransferService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TransferNotices.ensureChannel(this)
        startInForeground(TransferNotices.notification(this))
        if (intent?.action == TransferNotices.ACTION_STOP || !TransferNotices.running) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(TransferNotices.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(TransferNotices.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(TransferNotices.NOTIFICATION_ID, notification)
        }
    }
}

object TransferNotices {
    const val ACTION_STOP = "com.nytefall.nytegallery.STOP_TRANSFER"
    const val NOTIFICATION_ID = 7101
    private const val CHANNEL = "nyte_transfer"

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    private var latest: TransferProgress? = null

    fun show(context: Context, progress: TransferProgress) {
        val app = context.applicationContext
        latest = progress
        ensureChannel(app)
        if (!running) {
            running = true
            try {
                app.startForegroundService(Intent(app, TransferService::class.java))
            } catch (_: Exception) {
                running = false
            }
        } else {
            notify(app)
        }
    }

    fun hide(context: Context) {
        if (!running && latest == null) return
        running = false
        latest = null
        val app = context.applicationContext
        try {
            app.startService(Intent(app, TransferService::class.java).setAction(ACTION_STOP))
        } catch (_: Exception) {
            // The process is already going away.
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Vault transfer", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    fun notification(context: Context): Notification = build(context, latest)

    private fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, build(context, latest))
    }

    private fun build(context: Context, progress: TransferProgress?): Notification {
        val label = progress?.label ?: "Nyte Gallery"
        val text = when {
            progress == null -> "Working"
            progress.total <= 0 -> "Working"
            else -> "${progress.done} of ${progress.total}"
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(label)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress?.total?.coerceAtLeast(0) ?: 0, progress?.done ?: 0, progress == null || progress.total <= 0)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }
}
