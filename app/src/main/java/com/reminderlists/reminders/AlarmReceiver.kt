package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fires on an armed alarm (TZ 4.10). Recomputes next_fire_at and arms the next occurrence
// under goAsync() so the repeat chain never breaks.
// TODO presentation stage (TZ 4.5): notification / full-screen alert, SoundService,
//  reminder_events record, Monthly/Yearly date roll-over, auto-remove, DND/silent rules.
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        Logger.i("AlarmReceiver fired for reminder $reminderId")
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context, AppDatabase.get(context), reminderId)
            } finally {
                result.finish()
            }
        }
    }
}
