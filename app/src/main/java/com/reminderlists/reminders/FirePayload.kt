package com.reminderlists.reminders

import android.content.Intent
import com.reminderlists.data.db.entity.ReminderEntity

// Everything a fire needs after the alarm goes off — notification text, presentation flags and
// the sound settings — carried in intent extras instead of re-read from the database (TZ 4.10).
// Two users: SoundService, which must post the fire's own notification as its foreground
// notification, and the countdown timer, which owns no row at all (TZ 4.2 c′).
data class FirePayload(
    val id: Long,
    val title: String,
    val content: String?,
    val fullScreenAlert: Boolean,
    val loopSound: Boolean,
    val soundUri: String?,
    val repeatType: Int,
    val intervalUnit: Int?,
    // A timer has no row: Postpone re-arms it from this payload, OK simply closes the alert.
    val isTimer: Boolean,
) {
    val notificationId: Int get() = id.toInt()

    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_PRESENT, true)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_CONTENT, content)
        .putExtra(EXTRA_FULL_SCREEN, fullScreenAlert)
        .putExtra(EXTRA_LOOP_SOUND, loopSound)
        .putExtra(EXTRA_SOUND_URI, soundUri)
        .putExtra(EXTRA_REPEAT_TYPE, repeatType)
        .putExtra(EXTRA_INTERVAL_UNIT, intervalUnit ?: NO_UNIT)
        .putExtra(EXTRA_IS_TIMER, isTimer)

    // A reminder shaped like the fire, saved or not, so the shared notification and alert code
    // keeps taking a plain ReminderEntity (TZ 8).
    fun asReminder(): ReminderEntity = ReminderEntity(
        id = id,
        title = title,
        content = content,
        repeatType = repeatType,
        intervalUnit = intervalUnit,
        fullScreenAlert = fullScreenAlert,
        loopSound = loopSound,
        soundUri = soundUri,
        createdAt = 0L,
        updatedAt = 0L,
    )

    companion object {
        private const val EXTRA_PRESENT = "fire"
        private const val EXTRA_ID = "fire_id"
        private const val EXTRA_TITLE = "fire_title"
        private const val EXTRA_CONTENT = "fire_content"
        private const val EXTRA_FULL_SCREEN = "fire_full_screen"
        private const val EXTRA_LOOP_SOUND = "fire_loop_sound"
        private const val EXTRA_SOUND_URI = "fire_sound_uri"
        private const val EXTRA_REPEAT_TYPE = "fire_repeat_type"
        private const val EXTRA_INTERVAL_UNIT = "fire_interval_unit"
        private const val EXTRA_IS_TIMER = "fire_is_timer"
        private const val NO_UNIT = -1

        fun from(intent: Intent): FirePayload? {
            if (!intent.getBooleanExtra(EXTRA_PRESENT, false)) return null
            return FirePayload(
                id = intent.getLongExtra(EXTRA_ID, 0L),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                content = intent.getStringExtra(EXTRA_CONTENT),
                fullScreenAlert = intent.getBooleanExtra(EXTRA_FULL_SCREEN, false),
                loopSound = intent.getBooleanExtra(EXTRA_LOOP_SOUND, true),
                soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
                repeatType = intent.getIntExtra(EXTRA_REPEAT_TYPE, 0),
                intervalUnit = intent.getIntExtra(EXTRA_INTERVAL_UNIT, NO_UNIT).takeIf { it != NO_UNIT },
                isTimer = intent.getBooleanExtra(EXTRA_IS_TIMER, false),
            )
        }

        fun of(reminder: ReminderEntity): FirePayload = FirePayload(
            id = reminder.id,
            title = reminder.title,
            content = reminder.content,
            fullScreenAlert = reminder.fullScreenAlert,
            loopSound = reminder.loopSound,
            soundUri = reminder.soundUri,
            repeatType = reminder.repeatType,
            intervalUnit = reminder.intervalUnit,
            isTimer = false,
        )
    }
}
