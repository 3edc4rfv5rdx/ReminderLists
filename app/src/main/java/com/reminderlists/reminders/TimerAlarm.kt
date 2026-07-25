package com.reminderlists.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.util.Logger

// Countdown timer (TZ 4.2 c′): a one-shot alarm armed at «now + N», deliberately kept out of
// the database — nothing is inserted, no card appears in Once, nothing survives a reboot. The
// whole timer lives in the PendingIntent extras, so the fire path can rebuild what it needs
// without a single query. One timer at a time: a new start replaces the armed one (same id).
object TimerAlarm {
    // Fixed negative id, never colliding with a real reminder row (ids are > 0). Doubles as the
    // notification id and the PendingIntent request code, the way reminder ids do.
    const val TIMER_ID = -2L

    private const val EXTRA_TIMER = "timer"
    private const val EXTRA_TITLE = "timer_title"
    private const val EXTRA_CONTENT = "timer_content"
    private const val EXTRA_FULL_SCREEN = "timer_full_screen"
    private const val EXTRA_LOOP_SOUND = "timer_loop_sound"
    private const val EXTRA_SOUND_URI = "timer_sound_uri"

    // Everything the fire path needs, carried in the intent instead of a row.
    data class Spec(
        val title: String,
        val content: String?,
        val fullScreenAlert: Boolean,
        val loopSound: Boolean,
        val soundUri: String?,
    ) {
        fun putInto(intent: Intent): Intent = intent
            .putExtra(EXTRA_TIMER, true)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_CONTENT, content)
            .putExtra(EXTRA_FULL_SCREEN, fullScreenAlert)
            .putExtra(EXTRA_LOOP_SOUND, loopSound)
            .putExtra(EXTRA_SOUND_URI, soundUri)

        // A transient reminder — never saved — so the shared notification/alert code can take it
        // as usual (TZ 8: one notification builder for the whole app).
        fun asReminder(): ReminderEntity = ReminderEntity(
            id = TIMER_ID,
            title = title,
            content = content,
            repeatType = RepeatType.ONE_TIME.value,
            fullScreenAlert = fullScreenAlert,
            loopSound = loopSound,
            soundUri = soundUri,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    fun specFrom(intent: Intent): Spec? {
        if (!intent.getBooleanExtra(EXTRA_TIMER, false)) return null
        return Spec(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            content = intent.getStringExtra(EXTRA_CONTENT),
            fullScreenAlert = intent.getBooleanExtra(EXTRA_FULL_SCREEN, false),
            loopSound = intent.getBooleanExtra(EXTRA_LOOP_SOUND, true),
            soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
        )
    }

    // Arm the countdown. Same exact-alarm path as reminders (setAlarmClock, TZ 4.10) — only the
    // payload differs: extras instead of a row id.
    fun start(context: Context, spec: Spec, fireAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val showIntent = ReminderNotifier.openApp(context)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, showIntent), firePendingIntent(context, spec))
        Logger.i("Timer armed at $fireAtMillis")
    }

    // FLAG_UPDATE_CURRENT with a fixed request code: arming a new timer overwrites the extras of
    // the armed one, which is exactly the "one timer at a time" behaviour.
    private fun firePendingIntent(context: Context, spec: Spec): PendingIntent {
        val intent = spec.putInto(Intent(context, AlarmReceiver::class.java))
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, TIMER_ID)
        return PendingIntent.getBroadcast(
            context,
            TIMER_ID.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
