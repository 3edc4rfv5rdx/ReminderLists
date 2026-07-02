package com.reminderlists.util

import java.util.Locale

// Shared text normalization (no duplication, TZ 8).
object TextFormat {

    // Dictionary format "Xxxxxxxxx": first char upper, rest lower (TZ 3.4).
    fun toDictionaryForm(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase(Locale.getDefault())
        return lower.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    // Tag normalization: trim, collapse inner spaces, lowercase; drop empties (TZ 4.2).
    fun normalizeTag(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())

    // Split a comma-separated tag field into normalized, de-duplicated tokens (TZ 4.2).
    fun parseTags(raw: String): List<String> =
        raw.split(',')
            .map { normalizeTag(it) }
            .filter { it.isNotEmpty() }
            .distinct()
}
