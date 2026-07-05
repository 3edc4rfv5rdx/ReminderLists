package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Re-arms alarms after boot / app update, and recomputes next_fire_at on time/zone change
// (TZ 4.10). Alarms do not survive a reboot, so the re-arm is mandatory.
// TODO missed fires (TZ 4.10): grace window, catch-up, missed summary — presentation stage.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i("BootReceiver: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ReminderScheduler.rearmAll(context, AppDatabase.get(context))
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}
