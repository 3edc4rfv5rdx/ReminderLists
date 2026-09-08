package com.reminderlists.util

import java.util.Locale

// One item parsed out of the bulk input (TZ 3.3): text plus the optional "5/kg" amount.
data class ParsedItem(val text: String, val quantity: String?, val unit: String?)

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

    // Bulk input (TZ 3.3): one line of "bread, milk 2/l" becomes items. Commas, semicolons and
    // line breaks separate equally, so text shared out of the app can be pasted straight back.
    // Our own share header (">>> List name") is dropped with the rest of the list markers.
    private const val SHARE_HEADER_PREFIX = ">>>"
    private val WHITESPACE = Regex("\\s+")

    // Leading list markers: "- ", "* ", "1.", "[x]", our share text's "v "/"- ", check emoji.
    private val LEADING_MARKER = Regex("^(?:[-*•+‣]|\\[[ xX]?]|\\d{1,3}[.)]|[vVxX])\\s+|^[✓✔☑☐▢✅❌☒]\\s*")

    // Trailing amount in the same "5/kg" shape the item row shows and the dictionary stores.
    // The number is optional ("/kg"), the unit is optional ("2/"), but a bare word is not an
    // amount — "flour t/s" stays text.
    private val AMOUNT_TAIL = Regex("^(\\d+(?:[.,]\\d+)?)?/([^/]*)$")

    // Items are the same when their texts are, whatever the amount — one rule for collapsing
    // repeats inside the input and for spotting what the list already holds.
    fun bulkKey(text: String): String = toDictionaryForm(text)

    fun parseBulkItems(raw: String): List<ParsedItem> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<ParsedItem>()
        for (line in raw.lineSequence()) {
            if (line.trimStart().startsWith(SHARE_HEADER_PREFIX)) continue
            for (piece in line.split(',', ';')) {
                val item = parseBulkItem(piece) ?: continue
                if (seen.add(bulkKey(item.text))) items += item
            }
        }
        return items
    }

    // One piece of the bulk input; null when nothing but markers and spaces is left.
    private fun parseBulkItem(piece: String): ParsedItem? {
        val cleaned = piece.replace(WHITESPACE, " ").trim().replace(LEADING_MARKER, "").trim()
        if (cleaned.isEmpty()) return null
        val cut = cleaned.lastIndexOf(' ')
        if (cut > 0) {
            val amount = AMOUNT_TAIL.matchEntire(cleaned.substring(cut + 1))
            val quantity = amount?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
            val unit = amount?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            val text = cleaned.substring(0, cut).trim()
            val fits = (quantity?.length ?: 0) <= Limits.QUANTITY && (unit?.length ?: 0) <= Limits.UNIT
            if (text.isNotEmpty() && (quantity != null || unit != null) && fits) {
                return ParsedItem(text.take(Limits.ITEM_TEXT), quantity, unit)
            }
        }
        return ParsedItem(cleaned.take(Limits.ITEM_TEXT), null, null)
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
