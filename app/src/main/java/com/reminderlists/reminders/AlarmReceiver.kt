package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fires on an armed alarm (TZ 4.5 / 4.10). Under goAsync(): present the notification, record
// the fire in reminder_events, then recompute next_fire_at and arm the next occurrence so the
// repeat chain never breaks.
// TODO SoundService loop (feature 2), full-screen alert for fullScreenAlert (feature 3),
//  Monthly/Yearly date roll-over + auto-remove (feature 4).
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        Logger.i("AlarmReceiver fired for reminder $reminderId")
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val reminder = db.remindersDao().get(reminderId)
                // A cancelled/edited-away or silenced reminder may still deliver a stale
                // alarm — present only a live, active one while reminders are enabled.
                if (reminder != null && reminder.active && ReminderScheduler.remindersEnabled(db)) {
                    val now = System.currentTimeMillis()
                    db.remindersDao().insertEvent(
                        ReminderEventEntity(reminderId = reminderId, firedAt = now, createdAt = now),
                    )
                    ReminderNotifier.notifyFired(context, reminder)
                }
                ReminderScheduler.reschedule(context, db, reminderId)
            } finally {
                result.finish()
            }
        }
    }
}
