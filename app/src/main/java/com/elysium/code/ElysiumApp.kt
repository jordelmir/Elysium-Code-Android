package com.elysium.code

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ElysiumApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val inferenceChannel = NotificationChannel(
            CHANNEL_INFERENCE,
            "AI Inference",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Elysium AI is processing"
            setShowBadge(false)
        }

        val systemChannel = NotificationChannel(
            CHANNEL_SYSTEM,
            "System",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "System notifications"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(inferenceChannel)
        manager.createNotificationChannel(systemChannel)
    }

    companion object {
        lateinit var instance: ElysiumApp
            private set

        const val CHANNEL_INFERENCE = "elysium_inference"
        const val CHANNEL_SYSTEM = "elysium_system"
    }
}
