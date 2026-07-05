package com.reminderlists.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys

// Exact-alarm scheduling via AlarmManager.setAlarmClock (TZ 4.10). One alarm per reminder,
// PendingIntent keyed by reminder id. Only the nearest occurrence is armed; the next one is
// re-armed after firing. The enable_reminders toggle cancels armed alarms without clearing
// next_fire_at.
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"

    private const val STAGGER_SLOTS = 10
    private const val STAGGER_STEP_MS = 6_000L

    // A cached next_fire_at older than this at re-arm time is a genuine miss (device off,
    // force-stopped, reminders re-enabled). Within the window it is normal slack — Doze,
    // boot delay — and would just fire on time (TZ 4.10).
    private const val MISSED_GRACE_MS = 10 * 60 * 1000L

    // Recompute next_fire_at and arm or cancel the alarm for one reminder. The single
    // entry point for Save, Active toggle, delete-side cancel, post-fire re-arm and
    // rearmAll (TZ 4.10). next_fire_at is always updated; the alarm is armed only when
    // there is a future fire and enable_reminders is on.
    suspend fun reschedule(context: Context, db: AppDatabase, reminderId: Long) {
        val dao = db.remindersDao()
        val reminder = dao.get(reminderId) ?: run {
            cancel(context, reminderId)
            return
        }
        val dailyTimes =
            if (RepeatType.of(reminder.repeatType) == RepeatType.DAILY) {
                dao.getTimes(reminderId).map { it.time }
            } else {
                emptyList()
            }
        val next = NextFireCalculator.compute(reminder, dailyTimes, System.currentTimeMillis())
        dao.updateNextFire(reminderId, next)
        if (next != null && remindersEnabled(db)) {
            schedule(context, reminderId, next)
        } else {
            cancel(context, reminderId)
        }
    }

    // Re-arm all active reminders: boot, app update, app start (post force-stop), time/zone
    // change, enable_reminders back on (TZ 4.10). Detects missed fires from the stale cached
    // next_fire_at before rescheduling, then notifies once for all of them.
    // TODO within-grace misses want the normal on-time alert (full-screen) — TZ 4.5 stage.
    suspend fun rearmAll(context: Context, db: AppDatabase) {
        if (!remindersEnabled(db)) {
            Logger.i("rearmAll skipped: reminders disabled")
            return
        }
        val now = System.currentTimeMillis()
        val active = db.remindersDao().getActive()
        val missed = active.filter { it.nextFireAt?.let { fire -> fire < now - MISSED_GRACE_MS } == true }
        active.forEach { reschedule(context, db, it.id) }
        ReminderNotifier.notifyMissed(context, missed)
        Logger.i("rearmAll done: ${active.size} active, ${missed.size} missed")
    }

    // Silence mode (TZ 5): only cancels armed alarms, next_fire_at stays cached.
    suspend fun cancelAll(context: Context, db: AppDatabase) {
        db.remindersDao().getActive().forEach { cancel(context, it.id) }
        Logger.i("cancelAll done")
    }

    fun schedule(context: Context, reminderId: Long, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminderId)
        val showIntent = pendingIntent(context, reminderId) // TODO distinct show intent for the info action
        // Fire times are minute-granular, so same-minute reminders are staggered by id
        // within the minute (10 slots x 6 s) to keep sounds/alerts from colliding.
        // next_fire_at stays clean — the offset exists only on the armed alarm (TZ 4.10).
        val staggered = triggerAtMillis + (reminderId % STAGGER_SLOTS) * STAGGER_STEP_MS
        am.setAlarmClock(AlarmManager.AlarmClockInfo(staggered, showIntent), pi)
        Logger.i("Scheduled reminder $reminderId at $staggered")
    }

    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, reminderId))
    }

    // enable_reminders (TZ 5): stored as '1'/'0', absent means enabled.
    private suspend fun remindersEnabled(db: AppDatabase): Boolean =
        db.settingsDao().get(SettingsKeys.ENABLE_REMINDERS) != "0"

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
