package com.reminderlists.data.reminders

import com.reminderlists.data.db.entity.ReminderEntity

// Repeat type stored in reminders.repeatType (TZ 6.2).
enum class RepeatType(val value: Int) {
    ONE_TIME(0),
    DAILY(1),
    PERIOD(2),
    INTERVAL(3),
    ;

    companion object {
        fun of(value: Int): RepeatType = entries.firstOrNull { it.value == value } ?: ONE_TIME
    }
}

// Interval unit stored in reminders.intervalUnit (TZ 4.2 f‴ / 6.2). Years intentionally
// absent — "once a year" is the Yearly type. Minutes/hours are absolute (Instant) intervals;
// days/weeks/months are wall-clock (LocalDate) — the split is applied in NextFireCalculator.
enum class IntervalUnit(val value: Int) {
    MINUTES(0),
    HOURS(1),
    DAYS(2),
    WEEKS(3),
    MONTHS(4),
    ;

    companion object {
        fun of(value: Int?): IntervalUnit = entries.firstOrNull { it.value == value } ?: DAYS
    }
}

// A plain one-shot Once (not Monthly/Yearly, which roll forward): nothing left to fire once it
// has gone off, so it's retired to inactive at fire / catch-up (TZ 4.1 / 4.10).
fun ReminderEntity.isOneShotOnce(): Boolean =
    RepeatType.of(repeatType) == RepeatType.ONE_TIME && !monthlyRepeat && !yearlyRepeat

// The five fixed type-folders of the Reminders tab (TZ 4.1). Derived from the form fields,
// never stored or picked manually. Monthly/Yearly are mutually exclusive checkboxes, so the
// when-order carries no priority semantics.
enum class ReminderFolder {
    ONCE,
    DAILY,
    PERIODS,
    MONTHLY,
    YEARLY,
    INTERVALS,
    ;

    companion object {
        fun of(reminder: ReminderEntity): ReminderFolder =
            when (RepeatType.of(reminder.repeatType)) {
                RepeatType.DAILY -> DAILY
                RepeatType.PERIOD -> PERIODS
                RepeatType.INTERVAL -> INTERVALS
                RepeatType.ONE_TIME -> when {
                    reminder.monthlyRepeat -> MONTHLY
                    reminder.yearlyRepeat -> YEARLY
                    else -> ONCE
                }
            }
    }
}
