package com.reminderlists.reminders

import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.IntervalUnit
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
                val time = Dates.parseTime(reminder.time.orEmpty()) ?: return null
                val mask = reminder.weekdaysMask ?: Weekdays.NONE
                if (mask == Weekdays.NONE) return null
                val fromRaw = reminder.periodFrom.orEmpty()
                val toRaw = reminder.periodTo.orEmpty()
                val fromDate = Dates.parseDate(fromRaw)
                val toDate = Dates.parseDate(toRaw)
                if (fromDate != null && toDate != null) {
                    // Concrete one-shot range (dates).
                    val start = maxOf(fromDate, localDate(now, zone))
                    nextWeekdaySlot(start, toDate, listOf(time), mask, now, zone)
                } else {
                    // Recurring monthly day-window (bare day numbers).
                    val fromDay = Dates.parseDay(fromRaw) ?: return null
                    val toDay = Dates.parseDay(toRaw) ?: return null
                    nextMonthlyWindowSlot(fromDay, toDay, time, mask, now, zone)
                }
            }

            RepeatType.INTERVAL -> {
                val start = intervalStart(reminder) ?: return null
                val count = reminder.intervalCount?.takeIf { it >= 1 } ?: return null
                nextInterval(start, count, IntervalUnit.of(reminder.intervalUnit), now, zone)
            }
        }
    }

    /**
     * Fire times that fall on [today] for the Today dialog (TZ 4.11) — computed from the
     * schedule, not next_fire_at. Empty if the reminder is inactive or doesn't fire today.
     */
    fun todayOccurrences(
        reminder: ReminderEntity,
        dailyTimes: List<String>,
        today: LocalDate,
    ): List<LocalTime> {
        // Scheduled today regardless of active — Today is an overview; the UI strikes through
        // past times and whole inactive (fired / switched off) rows (TZ 4.11).
        val weekdayOn = { mask: Int -> Weekdays.has(mask, today.dayOfWeek.value - 1) }
        return when (RepeatType.of(reminder.repeatType)) {
            RepeatType.ONE_TIME -> {
                val date = Dates.parseDate(reminder.date.orEmpty()) ?: return emptyList()
                val time = Dates.parseTime(reminder.time.orEmpty()) ?: return emptyList()
                val firesToday = when {
                    reminder.monthlyRepeat -> dayOfMonthMatches(date, today)
                    reminder.yearlyRepeat -> date.month == today.month && dayOfMonthMatches(date, today)
                    else -> date == today
                }
                if (firesToday) listOf(time) else emptyList()
            }

            RepeatType.DAILY -> {
                val mask = reminder.weekdaysMask ?: Weekdays.NONE
                if (weekdayOn(mask)) dailyTimes.mapNotNull { Dates.parseTime(it) }.sorted() else emptyList()
            }

            RepeatType.PERIOD -> {
                val time = Dates.parseTime(reminder.time.orEmpty()) ?: return emptyList()
                val mask = reminder.weekdaysMask ?: Weekdays.NONE
                if (!weekdayOn(mask)) return emptyList()
                val fromRaw = reminder.periodFrom.orEmpty()
                val toRaw = reminder.periodTo.orEmpty()
                val fromDate = Dates.parseDate(fromRaw)
                val toDate = Dates.parseDate(toRaw)
                val inRange = if (fromDate != null && toDate != null) {
                    !today.isBefore(fromDate) && !today.isAfter(toDate)
                } else {
                    val fromDay = Dates.parseDay(fromRaw)
                    val toDay = Dates.parseDay(toRaw)
                    fromDay != null && toDay != null && inMonthlyWindow(today, fromDay, toDay)
                }
                if (inRange) listOf(time) else emptyList()
            }

            RepeatType.INTERVAL -> {
                val unit = IntervalUnit.of(reminder.intervalUnit)
                // Minute intervals would flood the Today dialog with hundreds of rows — excluded;
                // hours and coarser are listed like Daily (at most ~24 for hourly) (TZ 4.11).
                if (unit == IntervalUnit.MINUTES) return emptyList()
                val start = intervalStart(reminder) ?: return emptyList()
                val count = reminder.intervalCount?.takeIf { it >= 1 } ?: return emptyList()
                intervalOccurrencesOn(start, count, unit, today)
            }
        }
    }

    // Monthly/Yearly fire day within today's month, clamped to short months (anchor 31 -> the
    // month's last day), matches today's day-of-month (TZ 4.1).
    private fun dayOfMonthMatches(anchor: LocalDate, today: LocalDate): Boolean =
        minOf(anchor.dayOfMonth, today.lengthOfMonth()) == today.dayOfMonth

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

    // Recurring monthly day-window (TZ 4.2 e″/f″): nearest future slot on an active weekday
    // inside a [fromDay..toDay] window that repeats every month. A year-long scan always finds
    // one when any weekday is active.
    private fun nextMonthlyWindowSlot(
        fromDay: Int,
        toDay: Int,
        time: LocalTime,
        mask: Int,
        now: Long,
        zone: ZoneId,
    ): Long? {
        var day = localDate(now, zone)
        val limit = day.plusDays(366)
        while (!day.isAfter(limit)) {
            if (inMonthlyWindow(day, fromDay, toDay) && Weekdays.has(mask, day.dayOfWeek.value - 1)) {
                val at = toMillis(day, time, zone)
                if (at > now) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    // Whether a date falls in the monthly window [fromDay..toDay], each day clamped to the
    // month's length (31 -> last day). fromDay > toDay is a window spanning the month boundary
    // (28→3), matched as this month's tail (>= fromDay) or next month's head (<= toDay).
    private fun inMonthlyWindow(date: LocalDate, fromDay: Int, toDay: Int): Boolean {
        val len = date.lengthOfMonth()
        val from = minOf(fromDay, len)
        val to = minOf(toDay, len)
        val d = date.dayOfMonth
        return if (fromDay <= toDay) d in from..to else d >= from || d <= to
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

    // Interval start = the reminder's date + time (TZ 4.2 f‴). Null if either is unset/invalid.
    private fun intervalStart(reminder: ReminderEntity): LocalDateTime? {
        val date = Dates.parseDate(reminder.date.orEmpty()) ?: return null
        val time = Dates.parseTime(reminder.time.orEmpty()) ?: return null
        return LocalDateTime.of(date, time)
    }

    // Next interval fire strictly after now (TZ 4.10). Minutes/hours are absolute (Instant
    // arithmetic, DST-agnostic); days/weeks/months are wall-clock (LocalDateTime, months clamp
    // short-month days via java.time). Returns the first fire when the start is still in future.
    private fun nextInterval(
        start: LocalDateTime,
        count: Int,
        unit: IntervalUnit,
        now: Long,
        zone: ZoneId,
    ): Long {
        val startMillis = toMillis(start, zone)
        if (startMillis > now) return startMillis
        if (unit == IntervalUnit.MINUTES || unit == IntervalUnit.HOURS) {
            val step = count.toLong() * if (unit == IntervalUnit.MINUTES) 60_000L else 3_600_000L
            val k = (now - startMillis) / step + 1
            return startMillis + k * step
        }
        // Wall-clock units: estimate k from average step length, then correct with a bounded
        // scan (a few iterations) so DST/short-month drift can't misplace the fire.
        val approxStepMinutes = count.toLong() * when (unit) {
            IntervalUnit.DAYS -> 1_440L
            IntervalUnit.WEEKS -> 10_080L
            else -> 43_800L // months ~30.4 days, only a starting estimate
        }
        var k = maxOf(0L, (now - startMillis) / 60_000L / approxStepMinutes)
        while (k > 0 && toMillis(plusSteps(start, unit, count * k), zone) > now) k--
        while (toMillis(plusSteps(start, unit, count * k), zone) <= now) k++
        return toMillis(plusSteps(start, unit, count * k), zone)
    }

    // Wall-clock fire times of an Interval reminder falling on [today], for the Today dialog
    // (TZ 4.11). Bounded scan around today; caller excludes minute intervals.
    private fun intervalOccurrencesOn(
        start: LocalDateTime,
        count: Int,
        unit: IntervalUnit,
        today: LocalDate,
    ): List<LocalTime> {
        val dayStart = today.atStartOfDay()
        val dayEnd = today.plusDays(1).atStartOfDay()
        if (start.isAfter(dayEnd)) return emptyList()
        val approxStepMinutes = count.toLong() * when (unit) {
            IntervalUnit.HOURS -> 60L
            IntervalUnit.DAYS -> 1_440L
            IntervalUnit.WEEKS -> 10_080L
            else -> 43_800L
        }
        val minutesToDay = java.time.Duration.between(start, dayStart).toMinutes()
        var k = maxOf(0L, minutesToDay / approxStepMinutes)
        while (k > 0 && !plusSteps(start, unit, count * k).isBefore(dayStart)) k--
        val out = mutableListOf<LocalTime>()
        var guard = 0
        while (guard++ < 100_000) {
            val occ = plusSteps(start, unit, count * k)
            if (!occ.isBefore(dayEnd)) break
            if (!occ.isBefore(dayStart)) out += occ.toLocalTime()
            k++
        }
        return out
    }

    private fun plusSteps(start: LocalDateTime, unit: IntervalUnit, steps: Long): LocalDateTime =
        when (unit) {
            IntervalUnit.MINUTES -> start.plusMinutes(steps)
            IntervalUnit.HOURS -> start.plusHours(steps)
            IntervalUnit.DAYS -> start.plusDays(steps)
            IntervalUnit.WEEKS -> start.plusWeeks(steps)
            IntervalUnit.MONTHS -> start.plusMonths(steps)
        }

    private fun localDate(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    private fun toMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    private fun toMillis(dateTime: LocalDateTime, zone: ZoneId): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()
}
