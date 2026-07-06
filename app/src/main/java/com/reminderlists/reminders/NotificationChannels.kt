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
        // HIGH for heads-up + full-screen, but silent: sound and vibration are driven by
        // SoundService's own MediaPlayer/vibrator (TZ 4.10), so the channel must not add a
        // second system sound/vibration on top.
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE, "Service", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
