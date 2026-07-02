package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.util.Logger

// Fires on an armed alarm (TZ 4.10). Heavy work under goAsync() + wakelock: show notification
// or full-screen alert (4.5), start SoundService, record reminder_events, recompute
// next_fire_at, arm the next occurrence.
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        Logger.i("AlarmReceiver fired for reminder $reminderId")
        // TODO goAsync(): load reminder, respect enable_reminders, DND/silent rules (TZ 4.10),
        //  present alert, start SoundService, log event, recompute + re-arm.
    }
}
