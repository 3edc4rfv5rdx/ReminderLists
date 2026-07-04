package com.reminderlists.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.data.reminders.RemindersRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Reminders tab state (TZ 4.1 / 4.6): root shows the five fixed type-folders; opening one
// is in-tab state (bottom bar stays, tab state survives tab switches). One live query
// feeds folder counts and the opened folder's cards.
class RemindersViewModel(private val repo: RemindersRepository) : ViewModel() {

    private val currentFolderFlow = MutableStateFlow<ReminderFolder?>(null)
    val currentFolder: StateFlow<ReminderFolder?> = currentFolderFlow.asStateFlow()

    // All reminders grouped by their derived type-folder (TZ 4.1).
    private val byFolder: StateFlow<Map<ReminderFolder, List<ReminderWithDetails>>> =
        repo.observeAll()
            .map { all -> all.groupBy { ReminderFolder.of(it.reminder) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // folder -> reminders count shown next to folder names (styled like TZ 3.1 counters).
    val folderCounts: StateFlow<Map<ReminderFolder, Int>> =
        byFolder
            .map { groups -> groups.mapValues { (_, list) -> list.size } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Cards of the opened folder; the root (null) shows the Once group below the four
    // fixed folders (TZ 3.9). Sorting per TZ 4.6 reads the raw form fields; it switches
    // to next_fire_at once NextFireCalculator exists (TZ 4.10).
    val reminders: StateFlow<List<ReminderWithDetails>> =
        combine(currentFolderFlow, byFolder) { folder, groups ->
            val effective = folder ?: ReminderFolder.ONCE
            sorted(effective, groups[effective].orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openFolder(folder: ReminderFolder?) {
        currentFolderFlow.value = folder
    }

    fun setActive(reminder: ReminderEntity, active: Boolean) {
        viewModelScope.launch { repo.setActive(reminder, active) }
    }

    fun delete(reminder: ReminderEntity) {
        viewModelScope.launch { repo.delete(reminder) }
    }

    // 'YYYY-MM-DD' and 'HH:MM' sort correctly as plain strings.
    private fun sorted(folder: ReminderFolder, list: List<ReminderWithDetails>) = when (folder) {
        ReminderFolder.ONCE,
        ReminderFolder.MONTHLY,
        ReminderFolder.YEARLY,
        -> list.sortedWith(compareBy({ it.reminder.date.orEmpty() }, { it.reminder.time.orEmpty() }))

        ReminderFolder.DAILY ->
            list.sortedBy { detail -> detail.times.minOfOrNull { it.time }.orEmpty() }

        ReminderFolder.PERIODS ->
            list.sortedWith(compareBy({ it.reminder.periodFrom.orEmpty() }, { it.reminder.time.orEmpty() }))
    }

    companion object {
        val Factory = appViewModelFactory { db, app ->
            RemindersViewModel(RemindersRepository(db, app))
        }
    }
}
