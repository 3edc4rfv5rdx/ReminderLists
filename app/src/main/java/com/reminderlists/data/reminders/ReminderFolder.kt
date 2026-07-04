package com.reminderlists.data.reminders

import com.reminderlists.data.db.entity.ReminderEntity

// Repeat type stored in reminders.repeatType (TZ 6.2).
enum class RepeatType(val value: Int) {
    ONE_TIME(0),
    DAILY(1),
    PERIOD(2),
    ;

    companion object {
        fun of(value: Int): RepeatType = entries.firstOrNull { it.value == value } ?: ONE_TIME
    }
}

// The five fixed type-folders of the Reminders tab (TZ 4.1). Derived from the form fields,
// never stored or picked manually. Monthly/Yearly are mutually exclusive checkboxes, so the
// when-order carries no priority semantics.
enum class ReminderFolder {
    ONCE,
    DAILY,
    PERIODS,
    MONTHLY,
    YEARLY,
    ;

    companion object {
        fun of(reminder: ReminderEntity): ReminderFolder =
            when (RepeatType.of(reminder.repeatType)) {
                RepeatType.DAILY -> DAILY
                RepeatType.PERIOD -> PERIODS
                RepeatType.ONE_TIME -> when {
                    reminder.monthlyRepeat -> MONTHLY
                    reminder.yearlyRepeat -> YEARLY
                    else -> ONCE
                }
            }
    }
}
