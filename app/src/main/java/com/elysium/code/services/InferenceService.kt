package com.elysium.code.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.elysium.code.ElysiumApp
import com.elysium.code.R

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — InferenceService
 * ═══════════════════════════════════════════════════════════════
 *
 * Foreground service that keeps AI inference alive when the app
 * is in the background. Shows a persistent notification with
 * current status (generating, idle, etc.)
 */
class InferenceService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("AI Engine active"))
        return START_STICKY
    }

    fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun createNotification(status: String) =
        NotificationCompat.Builder(this, ElysiumApp.CHANNEL_INFERENCE)
            .setContentTitle("Elysium Code")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
