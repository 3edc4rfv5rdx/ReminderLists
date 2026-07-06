package com.reminderlists.data.filter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The two tabs that carry an independent filter state (TZ 3.9 / 4A.5). Lists is never filtered.
enum class FilterTab { REMINDERS, NOTES }

// Multi-tag combination on the Tag Filter screen (TZ 4.4): OR = any selected tag matches,
// AND = a record must carry all selected tags. User-toggled per tab.
enum class TagMode { OR, AND }

// The Top App Bar filter badge (TZ 3.9): All / T (Tag Filter only) / F (Filters only) / TF (both).
enum class FilterIndicator { ALL, T, F, TF }

// One tab's active filter — the single source of truth shared by the tab and the Filters /
// Tag Filter screens. The Filters (4.3) date/priority fields slot in here later; for now only
// the Tag Filter (4.4) portion is populated.
data class TabFilter(
    val tagIds: Set<Long> = emptySet(),
    val tagMode: TagMode = TagMode.OR,
) {
    val tagActive: Boolean get() = tagIds.isNotEmpty()

    // Wired when Filters (TZ 4.3) lands; keeps the indicator/AND logic forward-compatible.
    val fieldsActive: Boolean get() = false

    val isActive: Boolean get() = tagActive || fieldsActive

    val indicator: FilterIndicator
        get() = when {
            tagActive && fieldsActive -> FilterIndicator.TF
            tagActive -> FilterIndicator.T
            fieldsActive -> FilterIndicator.F
            else -> FilterIndicator.ALL
        }

    // Whether a record with these tag ids passes the tag portion of the filter (TZ 4.4).
    fun matchesTags(recordTagIds: Set<Long>): Boolean = when {
        !tagActive -> true
        tagMode == TagMode.OR -> recordTagIds.any { it in tagIds }
        else -> tagIds.all { it in recordTagIds }
    }
}

// In-memory, app-scoped holder for the two tabs' filters (single source of truth, TZ 8).
// Filters are transient UI narrowing — not persisted across process death.
object FilterStore {

    private val flows = FilterTab.entries.associateWith { MutableStateFlow(TabFilter()) }

    fun flow(tab: FilterTab): StateFlow<TabFilter> = flows.getValue(tab).asStateFlow()

    fun current(tab: FilterTab): TabFilter = flows.getValue(tab).value

    fun update(tab: FilterTab, transform: (TabFilter) -> TabFilter) {
        val state = flows.getValue(tab)
        state.value = transform(state.value)
    }

    // "Clear all filters" menu action (TZ 3.9) — resets every field of the tab's filter.
    fun clearAll(tab: FilterTab) = update(tab) { TabFilter() }
}
