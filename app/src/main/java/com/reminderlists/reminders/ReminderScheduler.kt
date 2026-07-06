package com.reminderlists.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.util.Limits
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys

// Exact-alarm scheduling via AlarmManager.setAlarmClock (TZ 4.10). One alarm per reminder,
// PendingIntent keyed by reminder id. Only the nearest occurrence is armed; the next one is
// re-armed after firing. The enable_reminders toggle cancels armed alarms without clearing
// next_fire_at.
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"

    // reminder_events.action values for the full-screen alert (TZ 4.5 / 6.2).
    const val ACTION_POSTPONE = "postpone"
    const val ACTION_OK = "ok"
    const val ACTION_DONE = "done"
    const val ACTION_CONTINUE = "continue"

    private const val STAGGER_SLOTS = 20
    private const val STAGGER_STEP_MS = 3_000L

    // A cached next_fire_at older than this at re-arm time is a genuine miss (device off,
    // force-stopped, reminders re-enabled). Within the window it is normal slack — Doze,
    // boot delay — and would just fire on time (TZ 4.10).
    private const val MISSED_GRACE_MS = Limits.MISSED_GRACE_MINUTES * 60 * 1000L

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
        val oldNext = reminder.nextFireAt
        val dailyTimes =
            if (RepeatType.of(reminder.repeatType) == RepeatType.DAILY) {
                dao.getTimes(reminderId).map { it.time }
            } else {
                emptyList()
            }
        val next = NextFireCalculator.compute(reminder, dailyTimes, System.currentTimeMillis())
        dao.updateNextFire(reminderId, next)
        val enabled = remindersEnabled(db)
        if (next != null && enabled) armMinute(context, db, next) else cancel(context, reminderId)
        // If this reminder left a minute, re-rank the group it vacated so the survivors keep
        // firing on the earliest slots (:00 first).
        if (enabled && oldNext != null && oldNext != next) armMinute(context, db, oldNext)
    }

    // Arm every active reminder sharing this fire minute, handing out slots lowest-id first: the
    // first fires exactly on time, the rest are staggered by STAGGER_STEP_MS so same-minute
    // sounds/alerts don't collide (TZ 4.10).
    private suspend fun armMinute(context: Context, db: AppDatabase, fireAt: Long) {
        db.remindersDao().idsFiringAt(fireAt).forEachIndexed { rank, id ->
            schedule(context, id, fireAt, rank)
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

    // Postpone from the full-screen alert (TZ 4.5): fire again at untilMillis. Overrides
    // next_fire_at directly (bypassing the schedule) and arms it. Re-activates a one-shot Once
    // whose Active was dropped at fire, so AlarmReceiver presents the postponed alarm.
    suspend fun postpone(context: Context, db: AppDatabase, reminderId: Long, untilMillis: Long) {
        val dao = db.remindersDao()
        val reminder = dao.get(reminderId) ?: return
        val now = System.currentTimeMillis()
        if (!reminder.active) dao.update(reminder.copy(active = true, updatedAt = now))
        dao.updateNextFire(reminderId, untilMillis)
        dao.insertEvent(
            ReminderEventEntity(
                reminderId = reminderId,
                action = ACTION_POSTPONE,
                postponeUntil = untilMillis,
                createdAt = now,
            ),
        )
        schedule(context, reminderId, untilMillis)
    }

    // OK on the alert (TZ 4.5): acknowledge and close. A one-shot Once already had Active
    // dropped at fire, so nothing is re-armed here; the ack is recorded for history and the
    // deferred auto-remove (TZ 4.2 h).
    suspend fun confirmOk(db: AppDatabase, reminderId: Long) {
        db.remindersDao().insertEvent(
            ReminderEventEntity(reminderId = reminderId, action = ACTION_OK, createdAt = System.currentTimeMillis()),
        )
    }

    // Period Done (TZ 4.5): close the period — stop firing. Clears Active, which recomputes
    // next_fire_at to null and cancels the occurrence armed at fire.
    suspend fun periodDone(context: Context, db: AppDatabase, reminderId: Long) {
        val dao = db.remindersDao()
        val reminder = dao.get(reminderId) ?: return
        dao.update(reminder.copy(active = false, updatedAt = System.currentTimeMillis()))
        dao.insertEvent(
            ReminderEventEntity(reminderId = reminderId, action = ACTION_DONE, createdAt = System.currentTimeMillis()),
        )
        reschedule(context, db, reminderId)
    }

    // Period Continue (TZ 4.5): keep firing within the period — the next occurrence was already
    // armed at fire, so this only records the choice.
    suspend fun periodContinue(db: AppDatabase, reminderId: Long) {
        db.remindersDao().insertEvent(
            ReminderEventEntity(reminderId = reminderId, action = ACTION_CONTINUE, createdAt = System.currentTimeMillis()),
        )
    }

    // Silence mode (TZ 5): only cancels armed alarms, next_fire_at stays cached.
    suspend fun cancelAll(context: Context, db: AppDatabase) {
        db.remindersDao().getActive().forEach { cancel(context, it.id) }
        Logger.i("cancelAll done")
    }

    // rank is the reminder's position among others firing the same minute (0 = fire exactly on
    // time). Only later positions get a stagger offset, so a lone reminder always fires at :00
    // (TZ 4.10). next_fire_at stays clean — the offset lives only on the armed alarm.
    fun schedule(context: Context, reminderId: Long, triggerAtMillis: Long, rank: Int = 0) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminderId)
        val showIntent = pendingIntent(context, reminderId) // TODO distinct show intent for the info action
        val staggered = triggerAtMillis + (rank % STAGGER_SLOTS) * STAGGER_STEP_MS
        am.setAlarmClock(AlarmManager.AlarmClockInfo(staggered, showIntent), pi)
        Logger.i("Scheduled reminder $reminderId at $staggered (rank $rank)")
    }

    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, reminderId))
    }

    // enable_reminders (TZ 5): stored as '1'/'0', absent means enabled.
    suspend fun remindersEnabled(db: AppDatabase): Boolean =
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
