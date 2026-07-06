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

    // Bare day-of-month 1..31 (TZ 4.2 e″/f″): a Period From/To may be a recurring monthly day
    // instead of a concrete date. null if the text isn't a plain day number.
    fun parseDay(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..31 }
}
