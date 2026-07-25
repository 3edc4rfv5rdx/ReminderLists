package com.reminderlists.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.data.reminders.isOneShotOnce
import com.reminderlists.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fires on an armed alarm (TZ 4.5 / 4.10). Under goAsync(): light up the screen, present the
// full-screen alert or a plain notification, start the looping sound/vibration, record the fire
// in reminder_events, drop a one-shot Once's Active flag, then recompute next_fire_at and arm
// the next occurrence so the repeat chain never breaks. Monthly/Yearly roll their next_fire_at
// forward via the recompute; a fired auto-remove Once is swept the next day (TZ 4.2 h, at app start).
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A countdown timer carries its whole payload in the intent and owns no row: present it
        // and stop — nothing to record, retire or re-arm (TZ 4.2 c′).
        FirePayload.from(intent)?.takeIf { it.isTimer }?.let { payload -> fireTimer(context, payload); return }

        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        Logger.i("AlarmReceiver fired for reminder $reminderId")
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                var reminder = db.remindersDao().get(reminderId)
                // A cancelled/edited-away or silenced reminder may still deliver a stale
                // alarm — present only a live, active one while reminders are enabled.
                if (reminder != null && reminder.active && ReminderScheduler.remindersEnabled(db)) {
                    wakeScreen(context)
                    val now = System.currentTimeMillis()
                    db.remindersDao().insertEvent(
                        ReminderEventEntity(reminderId = reminderId, firedAt = now, createdAt = now),
                    )
                    // Full-screen alert (TZ 4.5) when opted in, or always for Period (its
                    // Done/Continue buttons live only on that screen); otherwise a plain
                    // heads-up notification.
                    // Period forces the alert on (its Done/Continue buttons live only there),
                    // so the payload carries that decision to the sound service too.
                    val payload = FirePayload.of(reminder).copy(
                        fullScreenAlert = reminder.fullScreenAlert ||
                            RepeatType.of(reminder.repeatType) == RepeatType.PERIOD,
                    )
                    if (payload.fullScreenAlert) {
                        // Grab the screen immediately (works unlocked too, once the overlay
                        // grant is held) and post the full-screen-intent notification as the
                        // lockscreen fallback / shade presence.
                        ReminderNotifier.notifyFullScreen(context, payload)
                        FullScreenAlertActivity.start(context, reminderId)
                    } else {
                        ReminderNotifier.notifyFired(context, payload)
                    }
                    SoundService.start(context, payload)
                    // A plain Once has nothing left to fire — drop Active so the card shows
                    // it as done (Monthly/Yearly roll over, Daily/Period keep firing).
                    if (reminder.isOneShotOnce()) {
                        reminder = reminder.copy(active = false, updatedAt = now)
                        db.remindersDao().update(reminder)
                    }
                }
                ReminderScheduler.reschedule(context, db, reminderId)
            } finally {
                result.finish()
            }
        }
    }

    // Timer fire (TZ 4.2 c′): same presentation as a reminder — screen wake, full-screen alert or
    // heads-up notification, looping sound — built from the intent's transient reminder. The only
    // database touch is reading the global silence toggle (TZ 5), and nothing is written.
    private fun fireTimer(context: Context, payload: FirePayload) {
        Logger.i("AlarmReceiver fired for timer")
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!ReminderScheduler.remindersEnabled(AppDatabase.get(context))) return@launch
                wakeScreen(context)
                if (payload.fullScreenAlert) {
                    ReminderNotifier.notifyFullScreen(context, payload)
                    FullScreenAlertActivity.startTimer(context, payload)
                } else {
                    ReminderNotifier.notifyFired(context, payload)
                }
                SoundService.start(context, payload)
            } finally {
                result.finish()
            }
        }
    }

    // Briefly turn the screen on so the user can spot which device fired, regardless of the
    // system's ambient-display settings (TZ 4.5). SCREEN_BRIGHT_WAKE_LOCK is deprecated but
    // remains the way to wake the display from a receiver without launching an activity; the
    // timeout releases it.
    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "ReminderLists:fire",
        )
        wl.acquire(SCREEN_WAKE_MS)
    }

    private companion object {
        const val SCREEN_WAKE_MS = 10_000L
    }
}
