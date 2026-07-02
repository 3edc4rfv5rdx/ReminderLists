package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.util.Logger

// Re-arms alarms after boot / app update, and recomputes next_fire_at on time/zone change
// (TZ 4.10). Also resolves missed fires (grace window, catch-up, missed summary).
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i("BootReceiver: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                // TODO goAsync(): recompute next_fire_at for all active reminders, handle missed
                //  fires (TZ 4.10), then ReminderScheduler.rearmAll(context).
                ReminderScheduler.rearmAll(context)
            }
        }
    }
}
