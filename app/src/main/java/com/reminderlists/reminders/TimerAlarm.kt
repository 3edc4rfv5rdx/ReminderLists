package com.reminderlists.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.util.Logger

// Countdown timer (TZ 4.2 c′): a one-shot alarm armed at «now + N», deliberately kept out of
// the database — nothing is inserted, no card appears in Once, nothing survives a reboot. The
// whole timer lives in the PendingIntent extras (a FirePayload), so the fire path can rebuild
// what it needs without a single query. One timer at a time: a new start replaces the armed
// one (same id).
object TimerAlarm {
    // Fixed negative id, never colliding with a real reminder row (ids are > 0). Doubles as the
    // notification id and the PendingIntent request code, the way reminder ids do — hence it
    // comes from NotificationIds, where the whole negative range is allocated.
    const val TIMER_ID = NotificationIds.TIMER.toLong()

    // The timer's payload: a One time fire that owns no row.
    fun payload(
        title: String,
        content: String?,
        fullScreenAlert: Boolean,
        loopSound: Boolean,
        soundUri: String?,
    ): FirePayload = FirePayload(
        id = TIMER_ID,
        title = title,
        content = content,
        fullScreenAlert = fullScreenAlert,
        loopSound = loopSound,
        soundUri = soundUri,
        repeatType = RepeatType.ONE_TIME.value,
        intervalUnit = null,
        isTimer = true,
    )

    // Arm the countdown. Same exact-alarm path as reminders (setAlarmClock, TZ 4.10) — only the
    // payload differs: extras instead of a row id.
    // USE_EXACT_ALARM is declared in the manifest and auto-granted to alarm-clock apps, so no
    // runtime check applies here (TZ 4.10 / 9); lint only knows about SCHEDULE_EXACT_ALARM.
    @SuppressLint("MissingPermission")
    fun start(context: Context, payload: FirePayload, fireAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val showIntent = ReminderNotifier.openApp(context)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, showIntent), firePendingIntent(context, payload))
        // The only place an armed timer is visible from — and the only way to stop it, since it
        // owns no card anywhere in the app.
        ReminderNotifier.notifyTimerPending(context, payload, fireAtMillis)
        Logger.i("Timer armed at $fireAtMillis")
    }

    // Cancel the armed timer: drop the alarm and the countdown notification. A no-op when no
    // timer is armed (FLAG_NO_CREATE gives back null then).
    fun cancel(context: Context) {
        armedPendingIntent(context)?.let { pi ->
            context.getSystemService(AlarmManager::class.java).cancel(pi)
            pi.cancel()
            Logger.i("Timer cancelled")
        }
        ReminderNotifier.cancelTimerPending(context)
    }

    // The timer went off: the countdown is over, so its entry goes away and the fire's own
    // notification / alert takes over.
    fun clearPending(context: Context) {
        ReminderNotifier.cancelTimerPending(context)
    }

    // FLAG_UPDATE_CURRENT with a fixed request code: arming a new timer overwrites the extras of
    // the armed one, which is exactly the "one timer at a time" behaviour.
    // The armed timer's PendingIntent, or null when none is armed. Extras play no part in
    // matching an existing PendingIntent (component + action + data do), so a bare intent finds
    // whatever start() armed.
    private fun armedPendingIntent(context: Context): PendingIntent? = PendingIntent.getBroadcast(
        context,
        TIMER_ID.toInt(),
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun firePendingIntent(context: Context, payload: FirePayload): PendingIntent {
        val intent = payload.putInto(Intent(context, AlarmReceiver::class.java))
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, TIMER_ID)
        return PendingIntent.getBroadcast(
            context,
            TIMER_ID.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
