package com.reminderlists.data.filter

import com.reminderlists.data.db.dao.NoteWithDetails
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.util.Dates

// TagMode lives in FilterStore.kt (same package); referenced by matchesTagNames.

// Reminder-side application of a tab filter (TZ 4.3 / 4.4). Tag Filter and Filters are mutually
// exclusive, so at most one branch is active; an inactive filter passes everything.
fun TabFilter.matchesReminder(detail: ReminderWithDetails): Boolean = when {
    tagActive -> matchesTags(detail.tags.mapTo(HashSet()) { it.id })
    fieldsActive -> matchesDate(detail) && matchesPriority(detail) &&
        matchesTagNames(detail) && matchesActive(detail)
    else -> true
}

// Date range (TZ 4.3): only records that HAVE a date and fall outside the range are cut.
// Once/Monthly/Yearly use their date; Period uses overlap of its From–To with the filter
// range; Daily has no date and is never cut; unparseable dates pass.
private fun TabFilter.matchesDate(detail: ReminderWithDetails): Boolean {
    if (dateFrom == null && dateTo == null) return true
    // Bounds are validated 'YYYY-MM-DD' before a filter is applied, so these parse.
    val df = dateFrom?.let { Dates.parseDate(it) }
    val dt = dateTo?.let { Dates.parseDate(it) }
    val reminder = detail.reminder
    return when (ReminderFolder.of(reminder)) {
        ReminderFolder.DAILY -> true
        ReminderFolder.PERIODS -> {
            val from = reminder.periodFrom?.let { Dates.parseDate(it) }
            val to = reminder.periodTo?.let { Dates.parseDate(it) }
            if (from == null || to == null) return true
            // Overlap: period starts no later than the filter end AND ends no earlier than its start.
            (dt == null || from <= dt) && (df == null || to >= df)
        }
        else -> {
            val date = reminder.date?.let { Dates.parseDate(it) } ?: return true
            (df == null || date >= df) && (dt == null || date <= dt)
        }
    }
}

// Exact priority match (0 = any).
private fun TabFilter.matchesPriority(detail: ReminderWithDetails): Boolean =
    priority <= 0 || detail.reminder.priority == priority

// Named tags combined by the Filters OR/AND toggle (TZ 4.3).
private fun TabFilter.matchesTagNames(detail: ReminderWithDetails): Boolean {
    if (tagNames.isEmpty()) return true
    val recordNames = detail.tags.mapTo(HashSet()) { it.name.lowercase() }
    return if (tagNamesMode == TagMode.AND) {
        tagNames.all { it in recordNames }
    } else {
        tagNames.any { it in recordNames }
    }
}

// "Active only" toggle (TZ 4.3 addition): keep only reminders with Active on.
private fun TabFilter.matchesActive(detail: ReminderWithDetails): Boolean =
    !activeOnly || detail.reminder.active

// Note-side application of a tab filter (TZ 4A.5). Fields set is Date from/to, Tags, Priority —
// no Active (notes don't fire). Tag Filter and Filters are mutually exclusive.
fun TabFilter.matchesNote(detail: NoteWithDetails): Boolean = when {
    tagActive -> matchesTags(detail.tags.mapTo(HashSet()) { it.id })
    fieldsActive -> matchesNoteDate(detail) && matchesNotePriority(detail) && matchesNoteTagNames(detail)
    else -> true
}

// Date range (TZ 4A.5): only notes whose Date parses as 'YYYY-MM-DD' take part; an empty or
// unparseable Date is never cut by the date filter.
private fun TabFilter.matchesNoteDate(detail: NoteWithDetails): Boolean {
    if (dateFrom == null && dateTo == null) return true
    val date = detail.note.date?.let { Dates.parseDate(it) } ?: return true
    val df = dateFrom?.let { Dates.parseDate(it) }
    val dt = dateTo?.let { Dates.parseDate(it) }
    return (df == null || date >= df) && (dt == null || date <= dt)
}

private fun TabFilter.matchesNotePriority(detail: NoteWithDetails): Boolean =
    priority <= 0 || detail.note.priority == priority

private fun TabFilter.matchesNoteTagNames(detail: NoteWithDetails): Boolean {
    if (tagNames.isEmpty()) return true
    val recordNames = detail.tags.mapTo(HashSet()) { it.name.lowercase() }
    return if (tagNamesMode == TagMode.AND) {
        tagNames.all { it in recordNames }
    } else {
        tagNames.any { it in recordNames }
    }
}
