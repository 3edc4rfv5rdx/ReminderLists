package com.reminderlists.ui.screens.filters

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.filter.TabFilter
import com.reminderlists.data.filter.matchesReminder
import com.reminderlists.data.reminders.RemindersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Filters screen state (TZ 4.3): the tag dictionary for the «#» picker, plus a live snapshot
// of the tab's records so OK can warn when the drafted filter matches nothing.
class FiltersViewModel(db: AppDatabase, app: Application, private val tab: FilterTab) : ViewModel() {

    val allTagNames: StateFlow<List<String>> =
        db.tagsDao().observeTags()
            .map { tags -> tags.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reminders only; Notes has no list yet, so its match count check is skipped in the screen.
    private val reminders: StateFlow<List<ReminderWithDetails>> =
        if (tab == FilterTab.REMINDERS) {
            RemindersRepository(db, app).observeAll()
                // Eager: matchCount reads .value on OK without the UI collecting this flow.
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    // How many records the drafted filter would show (TZ 4.3), for the empty-result warning.
    fun matchCount(draft: TabFilter): Int = reminders.value.count { draft.matchesReminder(it) }

    companion object {
        fun factory(tab: FilterTab): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                FiltersViewModel(AppDatabase.get(app), app, tab)
            }
        }
    }
}
