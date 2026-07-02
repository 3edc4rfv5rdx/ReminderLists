package com.reminderlists.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.reminderlists.util.Logger

// Exact-alarm scheduling via AlarmManager.setAlarmClock (TZ 4.10). One alarm per reminder,
// PendingIntent keyed by reminder id. Only the nearest occurrence is armed; the next one is
// re-armed after firing. The enable_reminders toggle cancels armed alarms without clearing
// next_fire_at.
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun schedule(context: Context, reminderId: Long, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminderId)
        val showIntent = pendingIntent(context, reminderId) // TODO distinct show intent for the info action
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pi)
        Logger.i("Scheduled reminder $reminderId at $triggerAtMillis")
    }

    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, reminderId))
    }

    // TODO re-arm all active reminders (boot / update / time change), and cancel all when
    //  enable_reminders is turned off (TZ 4.10).
    fun rearmAll(context: Context) {
        Logger.i("rearmAll: TODO recompute next_fire_at and schedule active reminders")
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
