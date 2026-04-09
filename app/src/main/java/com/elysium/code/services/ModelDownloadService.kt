package com.elysium.code.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elysium.code.ElysiumApp

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — ModelDownloadService
 * ═══════════════════════════════════════════════════════════════
 *
 * Service for model asset extraction on first launch.
 * Shows progress notification during the extraction.
 * In production this handles the GGUF model copy from
 * APK assets → internal storage.
 */
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Preparing AI model...", 0))
        Log.i(TAG, "Model extraction service started")
        return START_NOT_STICKY
    }

    fun updateProgress(progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification("Extracting AI model...", progress))
    }

    private fun createNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, ElysiumApp.CHANNEL_SYSTEM)
            .setContentTitle("Elysium Code Setup")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
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
        Log.i(TAG, "Model extraction service stopped")
    }
}
