package com.reminderlists.reminders

import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.util.Dates
import com.reminderlists.util.Weekdays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId

// Computes reminders.next_fire_at (TZ 4.10). Wall-clock semantics: local date/time is the
// source of truth, converted to an instant via the device's CURRENT zone (java.time).
// DST uses java.time defaults per TZ 4.10: a nonexistent spring-forward time shifts
// forward, an ambiguous fall-back time takes the earlier offset and fires once.
// This is the single place all fire-time math lives (unit-tested per TZ 9).
object NextFireCalculator {

    /**
     * @param reminder the reminder to schedule
     * @param dailyTimes 'HH:MM' entries from reminder_times (Daily only)
     * @param now reference instant (unixtime millis)
     * @param zone zone to resolve wall-clock times in (device zone in production)
     * @return unixtime millis of the next fire strictly after now, or null if nothing
     *   to fire: inactive, invalid fields, Once already in the past (TZ 4.10)
     */
    fun compute(
        reminder: ReminderEntity,
        dailyTimes: List<String>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!reminder.active) return null
        return when (RepeatType.of(reminder.repeatType)) {
            RepeatType.ONE_TIME -> {
                val date = Dates.parseDate(reminder.date.orEmpty()) ?: return null
                val time = Dates.parseTime(reminder.time.orEmpty()) ?: return null
                when {
                    reminder.monthlyRepeat -> nextMonthly(date, time, now, zone)
                    reminder.yearlyRepeat -> nextYearly(date, time, now, zone)
                    else -> toMillis(date, time, zone).takeIf { it > now }
                }
            }

            RepeatType.DAILY -> {
                val times = dailyTimes.mapNotNull { Dates.parseTime(it) }.sorted()
                val mask = reminder.weekdaysMask ?: Weekdays.NONE
                if (times.isEmpty() || mask == Weekdays.NONE) return null
                // 8-day scan: today plus a full week always contains an active weekday.
                val today = localDate(now, zone)
                nextWeekdaySlot(today, today.plusDays(7), times, mask, now, zone)
            }

            RepeatType.PERIOD -> {
                val from = Dates.parseDate(reminder.periodFrom.orEmpty()) ?: return null
                val to = Dates.parseDate(reminder.periodTo.orEmpty()) ?: return null
                val time = Dates.parseTime(reminder.time.orEmpty()) ?: return null
                val mask = reminder.weekdaysMask ?: Weekdays.NONE
                if (mask == Weekdays.NONE) return null
                val start = maxOf(from, localDate(now, zone))
                nextWeekdaySlot(start, to, listOf(time), mask, now, zone)
            }
        }
    }

    // Nearest date in [start..end] whose weekday is in the mask, at the earliest of the
    // given times that is still in the future (Daily d′/f′, Period h″ — TZ 4.2).
    private fun nextWeekdaySlot(
        start: LocalDate,
        end: LocalDate,
        times: List<LocalTime>,
        mask: Int,
        now: Long,
        zone: ZoneId,
    ): Long? {
        var day = start
        while (!day.isAfter(end)) {
            // DayOfWeek: Mon=1..Sun=7; mask bit0 = Mon (TZ 6.2).
            if (Weekdays.has(mask, day.dayOfWeek.value - 1)) {
                for (time in times) {
                    val at = toMillis(day, time, zone)
                    if (at > now) return at
                }
            }
            day = day.plusDays(1)
        }
        return null
    }

    // Monthly: nearest future occurrence of the anchor day-of-month, clamped to short
    // months (31st -> Feb 28) while keeping the anchor for later months (TZ 4.1).
    private fun nextMonthly(anchor: LocalDate, time: LocalTime, now: Long, zone: ZoneId): Long {
        var month = localDate(now, zone).withDayOfMonth(1)
        while (true) {
            val day = minOf(anchor.dayOfMonth, month.lengthOfMonth())
            val at = toMillis(month.withDayOfMonth(day), time, zone)
            if (at > now) return at
            month = month.plusMonths(1)
        }
    }

    // Yearly: nearest future occurrence of the anchor day+month; Feb 29 falls back to
    // Feb 28 in non-leap years (TZ 4.1).
    private fun nextYearly(anchor: LocalDate, time: LocalTime, now: Long, zone: ZoneId): Long {
        var year = localDate(now, zone).year
        while (true) {
            val day = minOf(anchor.dayOfMonth, anchor.month.length(Year.isLeap(year.toLong())))
            val at = toMillis(LocalDate.of(year, anchor.month, day), time, zone)
            if (at > now) return at
            year++
        }
    }

    private fun localDate(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    private fun toMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
}
