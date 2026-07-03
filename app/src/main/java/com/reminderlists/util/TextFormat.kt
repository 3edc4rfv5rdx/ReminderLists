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

    // Dictionary entry with an optional unit suffix: "Молоко /kg" (TZ 3.4).
    fun toDictionaryEntry(name: String, unit: String?): String {
        val u = unit?.trim().orEmpty()
        val raw = if (u.isEmpty()) name else "$name /$u"
        return toDictionaryForm(raw)
    }

    // Split "Молоко /kg" back into name and unit; entries without " /" have no unit.
    fun splitDictionaryEntry(entry: String): Pair<String, String?> {
        val index = entry.lastIndexOf(" /")
        if (index < 0) return entry to null
        val unit = entry.substring(index + 2).trim().ifEmpty { null }
        return entry.substring(0, index).trim() to unit
    }

    // Quantity + unit display: "5/kg"; unit-only is "/kg", quantity-only is "5" (TZ 3.3).
    fun formatAmount(quantity: String?, unit: String?): String {
        val q = quantity.orEmpty()
        val u = unit.orEmpty()
        return if (u.isEmpty()) q else "$q/$u"
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
