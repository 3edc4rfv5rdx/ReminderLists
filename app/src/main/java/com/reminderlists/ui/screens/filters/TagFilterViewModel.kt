package com.reminderlists.ui.screens.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.TagUsage
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.filter.TabFilter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Tag Filter screen state (TZ 4.4): the tag cloud for the active tab, sorted by usage.
// Selection and OR/AND mode are local draft state in the composable, committed to FilterStore
// on OK — so cancelling (Back) leaves the applied filter untouched.
class TagFilterViewModel(db: AppDatabase, tab: FilterTab) : ViewModel() {

    private val dao = db.tagsDao()

    val tags: StateFlow<List<TagUsage>> =
        when (tab) {
            FilterTab.REMINDERS -> dao.observeReminderTagUsage()
            FilterTab.NOTES -> dao.observeNoteTagUsage()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // recordId -> its tag ids, for the active tab; lets OK preview whether a drafted filter
    // matches anything before applying (TZ 4.4). Untagged records never match a tag filter.
    private val recordTags: StateFlow<Map<Long, Set<Long>>> =
        when (tab) {
            FilterTab.REMINDERS -> dao.observeReminderTagLinks()
            FilterTab.NOTES -> dao.observeNoteTagLinks()
        }.map { links ->
            links.groupBy { it.recordId }.mapValues { (_, l) -> l.mapTo(HashSet()) { it.tagId } }
            // Eager: matchCount reads .value on OK without the UI ever collecting this flow.
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // How many records the drafted filter would show (TZ 4.4). An active tag filter matching
    // zero records warns the user instead of dropping them onto an empty tab.
    fun matchCount(draft: TabFilter): Int =
        recordTags.value.values.count { draft.matchesTags(it) }

    companion object {
        fun factory(tab: FilterTab): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                TagFilterViewModel(AppDatabase.get(app), tab)
            }
        }
    }
}
