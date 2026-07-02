package com.reminderlists.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

// Notification channels (TZ 4.10). Reminders = HIGH (heads-up + full-screen intent);
// Service = LOW (silent, for SoundService + missed-summary).
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val SERVICE = "service"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE, "Service", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
