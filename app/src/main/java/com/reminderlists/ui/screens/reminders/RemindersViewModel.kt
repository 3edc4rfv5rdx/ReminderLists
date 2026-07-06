package com.reminderlists.ui.screens.reminders

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.R
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.data.reminders.RemindersRepository
import com.reminderlists.reminders.NextFireCalculator
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.ui.components.SnackEvent
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
class RemindersViewModel(
    private val repo: RemindersRepository,
    private val app: Application,
) : ViewModel() {

    private val currentFolderFlow = MutableStateFlow<ReminderFolder?>(null)
    val currentFolder: StateFlow<ReminderFolder?> = currentFolderFlow.asStateFlow()

    // Validation feedback for the Active toggle (TZ 8 snackbar).
    var snack by mutableStateOf<SnackEvent?>(null)

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

    // Refuse to activate a reminder that has nothing left to fire (past one-time, a period
    // fully in the past, …) — it would sit checked but never ring (TZ 4.2). The card's checkbox
    // is bound to the DB state, so a refusal simply leaves it unchecked.
    fun setActive(detail: ReminderWithDetails, active: Boolean) {
        if (active) {
            val times = detail.times.map { it.time }
            val next = NextFireCalculator.compute(
                detail.reminder.copy(active = true),
                times,
                System.currentTimeMillis(),
            )
            if (next == null) {
                snack = SnackEvent.warning(app.getString(R.string.error_once_past))
                return
            }
        }
        viewModelScope.launch { repo.setActive(detail.reminder, active) }
    }

    fun delete(reminder: ReminderEntity) {
        viewModelScope.launch { repo.delete(reminder) }
    }

    // 'YYYY-MM-DD' and 'HH:MM' sort correctly as plain strings. Monthly/Yearly sort by the
    // rolled-forward next_fire_at (TZ 4.1), inactive ones (null) last.
    private fun sorted(folder: ReminderFolder, list: List<ReminderWithDetails>) = when (folder) {
        ReminderFolder.ONCE ->
            list.sortedWith(compareBy({ it.reminder.date.orEmpty() }, { it.reminder.time.orEmpty() }))

        ReminderFolder.MONTHLY,
        ReminderFolder.YEARLY,
        -> list.sortedWith(compareBy(nullsLast()) { it.reminder.nextFireAt })

        ReminderFolder.DAILY ->
            list.sortedBy { detail -> detail.times.minOfOrNull { it.time }.orEmpty() }

        ReminderFolder.PERIODS ->
            list.sortedWith(compareBy({ it.reminder.periodFrom.orEmpty() }, { it.reminder.time.orEmpty() }))
    }

    companion object {
        val Factory = appViewModelFactory { db, app ->
            RemindersViewModel(RemindersRepository(db, app), app)
        }
    }
}
