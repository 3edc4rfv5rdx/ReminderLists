package com.reminderlists.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

// Shared date/time text parsing for form fields and filters (TZ 4.2 / 4.3):
// strict 'YYYY-MM-DD' dates and 'HH:MM' times.
object Dates {

    private val DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), DATE)
    } catch (_: Exception) {
        null
    }

    fun parseTime(text: String): LocalTime? = try {
        LocalTime.parse(text.trim(), TIME)
    } catch (_: Exception) {
        null
    }

    fun format(date: LocalDate): String = DATE.format(date)

    fun format(time: LocalTime): String = TIME.format(time)

    // Period From/To day-only input (TZ 4.2 e″/f″): a full date parses as is; a bare day
    // number resolves to the nearest month, starting at notBefore (today for From, the
    // From date for To), where that day exists and is not earlier than notBefore.
    fun resolveDayOnly(text: String, notBefore: LocalDate): LocalDate? {
        val trimmed = text.trim()
        parseDate(trimmed)?.let { return it }
        val day = trimmed.toIntOrNull() ?: return null
        if (day !in 1..31) return null
        var month = notBefore.withDayOfMonth(1)
        // Scan forward until the day fits the month (skips e.g. 31 in short months).
        repeat(12) {
            if (day <= month.lengthOfMonth()) {
                val candidate = month.withDayOfMonth(day)
                if (!candidate.isBefore(notBefore)) return candidate
            }
            month = month.plusMonths(1)
        }
        return null
    }
}
