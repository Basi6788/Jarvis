package com.romeo.jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // App start hote hi Notification Channel bana lein
        // Taake Service bina crash kiye chal sake
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jarvis_channel", // ID jo Service me use hogi
                "Jarvis Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
