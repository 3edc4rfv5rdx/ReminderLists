package com.reminderlists.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.reminderlists.R

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
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SERVICE,
                context.getString(R.string.notif_channel_service),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
