package com.reminderlists.reminders

import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.util.Weekdays
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// next_fire_at math (TZ 9): all repeat types, end-of-month carry, Feb 29, DST shift.
// Zone is fixed so results don't depend on the machine running the tests.
class NextFireCalculatorTest {

    private val zone = ZoneId.of("Europe/Kyiv")

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun once(
        date: String?,
        time: String?,
        monthly: Boolean = false,
        yearly: Boolean = false,
        active: Boolean = true,
    ) = ReminderEntity(
        id = 1,
        title = "t",
        active = active,
        repeatType = 0,
        date = date,
        time = time,
        monthlyRepeat = monthly,
        yearlyRepeat = yearly,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun daily(mask: Int) = ReminderEntity(
        id = 1,
        title = "t",
        repeatType = 1,
        weekdaysMask = mask,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun period(from: String, to: String, time: String, mask: Int) = ReminderEntity(
        id = 1,
        title = "t",
        repeatType = 2,
        time = time,
        periodFrom = from,
        periodTo = to,
        weekdaysMask = mask,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun interval(date: String, time: String, count: Int, unit: Int, active: Boolean = true) =
        ReminderEntity(
            id = 1,
            title = "t",
            active = active,
            repeatType = 3,
            date = date,
            time = time,
            intervalCount = count,
            intervalUnit = unit,
            createdAt = 0,
            updatedAt = 0,
        )

    private fun compute(r: ReminderEntity, times: List<String> = emptyList(), now: Long) =
        NextFireCalculator.compute(r, times, now, zone)

    // Once (TZ 4.10): date+time, or null when inactive / already in the past.

    @Test
    fun onceFutureFiresAtDateTime() {
        val next = compute(once("2026-07-10", "12:30"), now = at("2026-07-06", "09:00"))
        assertEquals(at("2026-07-10", "12:30"), next)
    }

    @Test
    fun oncePastReturnsNull() {
        assertNull(compute(once("2026-07-10", "12:30"), now = at("2026-07-11", "09:00")))
    }

    @Test
    fun inactiveReturnsNull() {
        assertNull(compute(once("2026-07-10", "12:30", active = false), now = at("2026-07-06", "09:00")))
    }

    // Monthly (TZ 4.1): anchor day-of-month, clamped to short months, anchor kept after.

    @Test
    fun monthlyNextOccurrenceThisMonth() {
        val next = compute(once("2026-01-15", "09:00", monthly = true), now = at("2026-03-10", "12:00"))
        assertEquals(at("2026-03-15", "09:00"), next)
    }

    @Test
    fun monthlyEndOfMonthClampsToFebruary() {
        val next = compute(once("2026-01-31", "09:00", monthly = true), now = at("2026-02-10", "12:00"))
        assertEquals(at("2026-02-28", "09:00"), next)
    }

    @Test
    fun monthlyAnchorSurvivesShortMonth() {
        // After the clamped Feb 28 fire the anchor day 31 is back in March.
        val next = compute(once("2026-01-31", "09:00", monthly = true), now = at("2026-02-28", "10:00"))
        assertEquals(at("2026-03-31", "09:00"), next)
    }

    // Yearly (TZ 4.1): anchor day+month, Feb 29 falls back to Feb 28 off leap years.

    @Test
    fun yearlyFeb29ClampsInNonLeapYear() {
        val next = compute(once("2024-02-29", "09:00", yearly = true), now = at("2026-07-06", "12:00"))
        assertEquals(at("2027-02-28", "09:00"), next)
    }

    @Test
    fun yearlyFeb29FiresOnLeapYear() {
        val next = compute(once("2024-02-29", "09:00", yearly = true), now = at("2027-07-06", "12:00"))
        assertEquals(at("2028-02-29", "09:00"), next)
    }

    // Daily (TZ 4.10): nearest of reminder_times across active weekdays.

    @Test
    fun dailyPicksNextTimeToday() {
        // 2026-07-06 is a Monday.
        val next = compute(daily(Weekdays.ALL), listOf("09:00", "18:00"), now = at("2026-07-06", "12:00"))
        assertEquals(at("2026-07-06", "18:00"), next)
    }

    @Test
    fun dailyRollsToTomorrowAfterLastTime() {
        val next = compute(daily(Weekdays.ALL), listOf("09:00", "18:00"), now = at("2026-07-06", "19:00"))
        assertEquals(at("2026-07-07", "09:00"), next)
    }

    @Test
    fun dailySkipsInactiveWeekdays() {
        // Monday-only mask, Monday evening -> next Monday.
        val next = compute(daily(0b0000001), listOf("09:00"), now = at("2026-07-06", "19:00"))
        assertEquals(at("2026-07-13", "09:00"), next)
    }

    @Test
    fun dailyEmptyMaskReturnsNull() {
        assertNull(compute(daily(Weekdays.NONE), listOf("09:00"), now = at("2026-07-06", "12:00")))
    }

    // Period (TZ 4.10): nearest matching day within From..To at time.

    @Test
    fun periodStartsAtFrom() {
        // 2026-07-10 is a Friday.
        val r = period("2026-07-10", "2026-07-20", "10:00", Weekdays.WEEKDAYS)
        assertEquals(at("2026-07-10", "10:00"), compute(r, now = at("2026-07-06", "12:00")))
    }

    @Test
    fun periodSkipsWeekendInsideRange() {
        // After Friday's fire the mask skips Sat/Sun to Monday the 13th.
        val r = period("2026-07-10", "2026-07-20", "10:00", Weekdays.WEEKDAYS)
        assertEquals(at("2026-07-13", "10:00"), compute(r, now = at("2026-07-10", "11:00")))
    }

    @Test
    fun periodEndsAfterTo() {
        val r = period("2026-07-10", "2026-07-20", "10:00", Weekdays.WEEKDAYS)
        assertNull(compute(r, now = at("2026-07-20", "11:00")))
    }

    // Period as a recurring monthly day-window (TZ 4.2 e″/f″): bare day numbers, repeats monthly.

    @Test
    fun periodWindowFiresTodayInsideWindow() {
        // Days 5–11 every month; 2026-07-06 sits inside, 10:00 still ahead of 09:00.
        val r = period("5", "11", "10:00", Weekdays.ALL)
        assertEquals(at("2026-07-06", "10:00"), compute(r, now = at("2026-07-06", "09:00")))
    }

    @Test
    fun periodWindowRollsToNextMonthWhenPast() {
        // After the 11th this month's window is done — next is the 5th of next month.
        val r = period("5", "11", "10:00", Weekdays.ALL)
        assertEquals(at("2026-08-05", "10:00"), compute(r, now = at("2026-07-15", "12:00")))
    }

    @Test
    fun periodWindowSpansMonthBoundaryTail() {
        // 28–3 spans the boundary: from the 15th the next hit is this month's 28th.
        val r = period("28", "3", "10:00", Weekdays.ALL)
        assertEquals(at("2026-07-28", "10:00"), compute(r, now = at("2026-07-15", "12:00")))
    }

    @Test
    fun periodWindowSpansMonthBoundaryHead() {
        // Still inside the 28→3 window on the 2nd of the next month.
        val r = period("28", "3", "10:00", Weekdays.ALL)
        assertEquals(at("2026-08-02", "10:00"), compute(r, now = at("2026-08-02", "09:00")))
    }

    @Test
    fun periodWindowSkipsToNextMonthOnWeekdayMask() {
        // Days 5–11 on weekdays only; 2026-07-11 is a Saturday and the window's weekdays have
        // passed, so the next hit is 2026-08-05 (a Wednesday).
        val r = period("5", "11", "10:00", Weekdays.WEEKDAYS)
        assertEquals(at("2026-08-05", "10:00"), compute(r, now = at("2026-07-11", "08:00")))
    }

    // Period Done (TZ 4.5): the rest of the closed window is skipped, Active is untouched.

    @Test
    fun periodWindowEndIsLastDayOfCurrentWindow() {
        val r = period("5", "11", "10:00", Weekdays.ALL)
        val end = NextFireCalculator.periodWindowEnd(r, at("2026-07-06", "10:01"), zone)
        assertEquals(at("2026-07-12", "00:00") - 1, end)
    }

    @Test
    fun periodWindowEndSpansMonthBoundary() {
        // 28→3: pressed on the 29th, the window still runs to the 3rd of the next month.
        val r = period("28", "3", "10:00", Weekdays.ALL)
        val end = NextFireCalculator.periodWindowEnd(r, at("2026-07-29", "10:01"), zone)
        assertEquals(at("2026-08-04", "00:00") - 1, end)
    }

    @Test
    fun periodWindowEndNullOutsideWindow() {
        val r = period("5", "11", "10:00", Weekdays.ALL)
        assertNull(NextFireCalculator.periodWindowEnd(r, at("2026-07-20", "10:00"), zone))
    }

    @Test
    fun periodDoneSkipsRestOfWindowAndResumesNextMonth() {
        val r = period("5", "11", "10:00", Weekdays.ALL)
        val done = at("2026-07-06", "10:01")
        val closed = r.copy(periodSkipUntil = NextFireCalculator.periodWindowEnd(r, done, zone))
        // The 7th..11th are skipped; the next window's first day fires as usual.
        assertEquals(at("2026-08-05", "10:00"), compute(closed, now = done))
    }

    @Test
    fun periodDoneOnDatedRangeLeavesNothingToFire() {
        val r = period("2026-07-10", "2026-07-20", "10:00", Weekdays.ALL)
        val done = at("2026-07-12", "10:01")
        val closed = r.copy(periodSkipUntil = NextFireCalculator.periodWindowEnd(r, done, zone))
        assertNull(compute(closed, now = done))
    }

    @Test
    fun periodSkipInThePastDoesNotAffectFiring() {
        val r = period("5", "11", "10:00", Weekdays.ALL)
            .copy(periodSkipUntil = at("2026-06-12", "00:00") - 1)
        assertEquals(at("2026-07-06", "10:00"), compute(r, now = at("2026-07-06", "09:00")))
    }

    // DST (TZ 4.10): java.time defaults — nonexistent spring-forward time shifts forward.

    @Test
    fun dailySpringForwardShiftsNonexistentTime() {
        // Kyiv 2026-03-29: clocks jump 03:00 -> 04:00, so 03:30 becomes 04:30 EEST.
        val next = compute(daily(Weekdays.ALL), listOf("03:30"), now = at("2026-03-28", "12:00"))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), next)
    }

    // Interval (TZ 4.10): start + k·interval, minutes/hours absolute, days/weeks/months wall-clock.

    @Test
    fun intervalFirstFireIsStartWhenInFuture() {
        val r = interval("2026-07-10", "08:00", 15, 0) // every 15 minutes
        assertEquals(at("2026-07-10", "08:00"), compute(r, now = at("2026-07-09", "23:00")))
    }

    @Test
    fun intervalMinutesStepsPastNow() {
        // Started 08:00, every 15 min; at 08:37 the next fire is 08:45.
        val r = interval("2026-07-10", "08:00", 15, 0)
        assertEquals(at("2026-07-10", "08:45"), compute(r, now = at("2026-07-10", "08:37")))
    }

    @Test
    fun intervalHoursStepsPastNow() {
        // Every 2 hours from 08:00; at 13:10 the next is 14:00.
        val r = interval("2026-07-10", "08:00", 2, 1)
        assertEquals(at("2026-07-10", "14:00"), compute(r, now = at("2026-07-10", "13:10")))
    }

    @Test
    fun intervalDaysStepsPastNow() {
        // Every 10 days from 2026-07-01 09:00; after 2026-07-15 the next is 2026-07-21.
        val r = interval("2026-07-01", "09:00", 10, 2)
        assertEquals(at("2026-07-21", "09:00"), compute(r, now = at("2026-07-15", "12:00")))
    }

    @Test
    fun intervalMonthsClampsShortMonth() {
        // Every 1 month from Jan 31: java.time clamps Feb to the 28th, keeping the anchor.
        val r = interval("2026-01-31", "09:00", 1, 4)
        assertEquals(at("2026-02-28", "09:00"), compute(r, now = at("2026-02-01", "09:00")))
    }

    @Test
    fun intervalHoursAreAbsoluteAcrossDst() {
        // Hours are an absolute (Instant) step: 30 min after the start the next fire is exactly
        // one real hour after the start, regardless of the 03:00->04:00 spring-forward jump.
        val r = interval("2026-03-29", "01:30", 1, 1) // every 1 hour
        val start = at("2026-03-29", "01:30")
        assertEquals(start + 60 * 60_000L, compute(r, now = start + 30 * 60_000L))
    }

    @Test
    fun intervalInactiveReturnsNull() {
        assertNull(compute(interval("2026-07-10", "08:00", 15, 0, active = false), now = at("2026-07-09", "12:00")))
    }
}
