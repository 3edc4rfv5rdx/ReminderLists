package com.reminderlists.ui.screens.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.dao.NoteWithDetails
import com.reminderlists.data.db.dao.SettingsDao
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import com.reminderlists.data.filter.FilterStore
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.filter.TabFilter
import com.reminderlists.data.filter.matchesNote
import com.reminderlists.data.notes.NotesRepository
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.Dates
import com.reminderlists.util.Limits
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Notes tab state (TZ 4A). Root shows folders + root note cards; opening a folder is in-tab
// state (bottom bar stays, tab state survives tab switches). One live query feeds folder
// counts and the opened folder's cards, all narrowed by the tab's own filter (TZ 4A.5).
class NotesViewModel(
    private val repo: NotesRepository,
    settingsDao: SettingsDao,
    private val app: Application,
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<Long?>(null)

    // Default PIN from Settings (TZ 4A.4 / 3.6); null or empty = not set.
    val defaultPin: StateFlow<String?> =
        settingsDao.observe(SettingsKeys.DEFAULT_PIN)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folders: StateFlow<List<NoteFolderEntity>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Folder currently opened; null = root. Falls back to root if the folder disappears.
    val currentFolder: StateFlow<NoteFolderEntity?> =
        combine(currentFolderId, folders) { id, all -> all.find { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // The tab's active filter (TZ 4A.5), shared source of truth for the Filters / Tag Filter
    // screens and the Top App Bar indicator.
    val filter: StateFlow<TabFilter> = FilterStore.flow(FilterTab.NOTES)

    // All notes grouped by their folder id (null = root), after the active filter — so a
    // folder's counter matches what opening it shows.
    private val filteredByFolder: StateFlow<Map<Long?, List<NoteWithDetails>>> =
        combine(repo.observeAll(), filter) { all, f ->
            val visible = if (f.isActive) all.filter { f.matchesNote(it) } else all
            visible.groupBy { it.note.folderId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // folderId -> notes count shown next to folder names (styled like TZ 3.1 / 4A.1 counters).
    val folderCounts: StateFlow<Map<Long, Int>> =
        filteredByFolder
            .map { groups ->
                groups.entries.mapNotNull { (id, list) -> id?.let { it to list.size } }.toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Cards of the opened folder (root = null), sorted per TZ 4A.5.
    val notes: StateFlow<List<NoteWithDetails>> =
        combine(currentFolderId, filteredByFolder) { folderId, groups ->
            sorted(groups[folderId].orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openFolder(folder: NoteFolderEntity?) {
        currentFolderId.value = folder?.id
    }

    // "Clear all filters" menu action (TZ 3.9).
    fun clearFilters() = FilterStore.clearAll(FilterTab.NOTES)

    // Sort (TZ 4A.5): notes whose Date parses as 'YYYY-MM-DD' first, oldest on top; the rest
    // (empty or unparseable Date) go to the end, ordered by Title.
    private fun sorted(list: List<NoteWithDetails>): List<NoteWithDetails> {
        val (dated, undated) = list.partition { Dates.parseDate(it.note.date.orEmpty()) != null }
        return dated.sortedBy { Dates.parseDate(it.note.date.orEmpty()) } +
            undated.sortedBy { it.note.title.lowercase() }
    }

    // Folder actions (TZ 4A.1).

    fun createFolder(name: String, comment: String?) {
        viewModelScope.launch { repo.createFolder(name, comment) }
    }

    fun renameFolder(folder: NoteFolderEntity, name: String) {
        viewModelScope.launch { repo.renameFolder(folder, name) }
    }

    fun updateFolderComment(folder: NoteFolderEntity, comment: String?) {
        viewModelScope.launch { repo.updateFolderComment(folder, comment) }
    }

    fun deleteFolder(folder: NoteFolderEntity, deleteNotes: Boolean) {
        viewModelScope.launch { repo.deleteFolder(folder, deleteNotes) }
    }

    // Note actions (TZ 4A.3 / 4A.4).

    fun moveNote(note: NoteEntity, folderId: Long?) {
        viewModelScope.launch { repo.moveNote(note, folderId) }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }

    // PIN gate check (TZ 4A.4 / 3.6): the note's own PIN, or the default PIN when none is set.
    fun pinMatches(note: NoteEntity, entered: String): Boolean {
        val expected = note.pinCode ?: defaultPin.value
        return !expected.isNullOrEmpty() && entered == expected
    }

    fun setProtection(note: NoteEntity, enabled: Boolean, customPin: String?) {
        viewModelScope.launch { repo.setNoteProtection(note, enabled, customPin) }
    }

    // Photos viewed straight from a card (TZ 4A.3): the live `notes` flow refreshes the open
    // viewer after an add/delete. Add is capped at the shared limit (TZ 8).
    fun addPhoto(noteId: Long, source: Uri) {
        viewModelScope.launch {
            if (repo.getPhotos(noteId).size >= Limits.MAX_PHOTOS) return@launch
            val fileName = PhotoManager.importPhoto(app, source) ?: return@launch
            repo.addPhoto(noteId, fileName)
        }
    }

    fun deletePhoto(photo: NotePhotoEntity) {
        viewModelScope.launch { repo.deletePhoto(photo) }
    }

    companion object {
        val Factory = appViewModelFactory { db, app ->
            NotesViewModel(NotesRepository(db, app), db.settingsDao(), app)
        }
    }
}
