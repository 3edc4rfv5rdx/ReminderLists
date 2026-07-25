package com.reminderlists.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Handles the plain reminder notification's action buttons (TZ 4.5): +10 min postpones the
// reminder and clears the alert, Stop only silences the looping sound while the notification
// stays in the shade. Runs in the background so neither opens the UI.
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Countdown timer (TZ 4.2 c′): no row behind it, so +10 min re-arms the alarm straight
        // from the carried payload and Stop just silences the sound.
        FirePayload.from(intent)?.takeIf { it.isTimer }?.let { spec ->
            when (intent.action) {
                ACTION_STOP -> SoundService.stop(context)
                ACTION_POSTPONE -> {
                    Logger.i("Notification +10 min for timer")
                    TimerAlarm.start(context, spec, System.currentTimeMillis() + POSTPONE_MS)
                    SoundService.stop(context)
                    ReminderNotifier.cancel(context, TimerAlarm.TIMER_ID)
                }
            }
            return
        }

        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        when (intent.action) {
            // Stop the sound only; leave the notification up (TZ 4.5).
            ACTION_STOP -> {
                Logger.i("Notification Stop for reminder $reminderId")
                SoundService.stop(context)
            }
            // Postpone 10 min: re-arm, silence the sound and dismiss the notification (TZ 4.5).
            ACTION_POSTPONE -> {
                Logger.i("Notification +10 min for reminder $reminderId")
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.get(context)
                        ReminderScheduler.postpone(context, db, reminderId, System.currentTimeMillis() + POSTPONE_MS)
                        SoundService.stop(context)
                        ReminderNotifier.cancel(context, reminderId)
                    } finally {
                        result.finish()
                    }
                }
            }
            // Stop repeating (Interval): deactivate so it stops firing, silence and dismiss (TZ 4.5).
            ACTION_DISMISS -> {
                Logger.i("Notification Stop repeating for reminder $reminderId")
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.get(context)
                        ReminderScheduler.deactivate(context, db, reminderId)
                        SoundService.stop(context)
                        ReminderNotifier.cancel(context, reminderId)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_POSTPONE = "com.reminderlists.action.POSTPONE_10"
        private const val ACTION_STOP = "com.reminderlists.action.STOP_ALERT"
        private const val ACTION_DISMISS = "com.reminderlists.action.STOP_REPEATING"
        private const val POSTPONE_MS = 10 * 60_000L

        fun postponeIntent(context: Context, reminderId: Long, timer: FirePayload? = null): PendingIntent =
            pendingIntent(context, reminderId, ACTION_POSTPONE, timer)

        fun stopIntent(context: Context, reminderId: Long, timer: FirePayload? = null): PendingIntent =
            pendingIntent(context, reminderId, ACTION_STOP, timer)

        fun dismissIntent(context: Context, reminderId: Long): PendingIntent =
            pendingIntent(context, reminderId, ACTION_DISMISS)

        // Request code keyed by id; the distinct action keeps postpone and stop separate for the
        // same reminder.
        private fun pendingIntent(
            context: Context,
            reminderId: Long,
            action: String,
            timer: FirePayload? = null,
        ): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            timer?.putInto(intent)
            return PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
