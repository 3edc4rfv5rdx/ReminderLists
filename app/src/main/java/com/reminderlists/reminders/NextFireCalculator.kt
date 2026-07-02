package com.reminderlists.reminders

import com.reminderlists.data.db.entity.ReminderEntity

// Computes reminders.next_fire_at (TZ 4.10). Wall-clock semantics: local date/time is the
// source of truth, converted to an instant via the device's CURRENT zone (java.time).
// This is the single place all fire-time math lives (unit-tested per TZ 9).
object NextFireCalculator {

    /**
     * @param reminder the reminder to schedule
     * @param dailyTimes 'HH:MM' entries from reminder_times (Daily only)
     * @param now reference instant (unixtime seconds)
     * @return unixtime of the next fire, or null if nothing to fire (TZ 4.10)
     */
    fun compute(
        reminder: ReminderEntity,
        dailyTimes: List<String>,
        now: Long,
    ): Long? {
        if (!reminder.active) return null
        // TODO implement per repeat_type:
        //   Once  -> date + time
        //   Monthly/Yearly -> next future date by day-of-month / day+month (end-of-month, Feb 29)
        //   Daily -> nearest of dailyTimes across active weekdays (weekdaysMask)
        //   Period -> nearest matching day within period_from..period_to and weekdaysMask, at time
        // DST: spring-forward shifts nonexistent local time; fall-back takes earlier offset.
        return null
    }
}
