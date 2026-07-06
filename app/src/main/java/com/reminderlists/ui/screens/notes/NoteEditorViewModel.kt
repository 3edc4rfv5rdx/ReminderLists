package com.reminderlists.ui.screens.notes

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.R
import com.reminderlists.data.db.dao.TagsDao
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import com.reminderlists.data.notes.NotesRepository
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.ui.components.SnackEvent
import com.reminderlists.util.Limits
import com.reminderlists.util.TextFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// One photo in the editor: existing rows carry the DB entity, new ones only the file name
// (attached on Save; the file is already imported into photos/).
data class NoteEditorPhoto(val entity: NotePhotoEntity?, val fileName: String)

// Note add/edit form state (TZ 4A.2): Title (required), Content, Date (free text), Photos,
// Tags, Priority. No firing fields; PIN is toggled from the card menu (TZ 4A.4), not here.
class NoteEditorViewModel(
    private val repo: NotesRepository,
    tagsDao: TagsDao,
    application: Application,
    noteId: Long,
    private val initialFolderId: Long?,
) : ViewModel() {

    private val appContext = application.applicationContext
    val isEdit = noteId > 0
    private var existing: NoteEntity? = null

    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var date by mutableStateOf("")
    var tags by mutableStateOf("")
    var priority by mutableIntStateOf(0)

    val photos = mutableStateListOf<NoteEditorPhoto>()
    private var saved = false

    // Validation errors surface through the shared snackbar (TZ 4A.2 / 8).
    var snack by mutableStateOf<SnackEvent?>(null)

    // Tag dictionary for the «#» picker (TZ 4.2 п. 3).
    val allTags: StateFlow<List<String>> =
        tagsDao.observeTags()
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (isEdit) {
            viewModelScope.launch {
                repo.getWithDetails(noteId)?.let { detail ->
                    val n = detail.note
                    existing = n
                    title = n.title
                    content = n.content.orEmpty()
                    date = n.date.orEmpty()
                    tags = detail.tags.joinToString(", ") { it.name }
                    priority = n.priority
                    detail.photos.forEach { photos += NoteEditorPhoto(it, it.filePath) }
                }
            }
        }
    }

    fun addPhoto(source: Uri) {
        if (photos.size >= Limits.MAX_PHOTOS) return
        viewModelScope.launch {
            val fileName = PhotoManager.importPhoto(appContext, source) ?: return@launch
            photos += NoteEditorPhoto(null, fileName)
        }
    }

    fun deletePhoto(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        viewModelScope.launch {
            if (photo.entity != null) {
                repo.deletePhoto(photo.entity)
            } else {
                PhotoManager.delete(appContext, photo.fileName)
            }
            photos.remove(photo)
        }
    }

    fun save(onSaved: () -> Unit) {
        if (title.isBlank()) {
            snack = SnackEvent.error(appContext.getString(R.string.error_title_required))
            return
        }
        val now = System.currentTimeMillis()
        val base = existing
        val entity = NoteEntity(
            id = base?.id ?: 0,
            // Keep the folder and PIN of the note being edited; new notes inherit the opened folder.
            folderId = base?.folderId ?: initialFolderId,
            title = title.trim(),
            content = content.trim().takeIf { it.isNotEmpty() },
            // Free text, not validated (TZ 4A.2); stored exactly as entered.
            date = date.trim().takeIf { it.isNotEmpty() },
            priority = priority,
            pinEnabled = base?.pinEnabled ?: false,
            pinCode = base?.pinCode,
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            val id = repo.save(entity, TextFormat.parseTags(tags))
            photos.filter { it.entity == null }.forEach { repo.addPhoto(id, it.fileName) }
            saved = true
            onSaved()
        }
    }

    // Back without Save: drop imported-but-unattached files (crash leftovers are caught
    // by the startup orphan sweep, TZ 8).
    override fun onCleared() {
        if (!saved) {
            photos.filter { it.entity == null }
                .forEach { PhotoManager.fileFor(appContext, it.fileName).delete() }
        }
    }

    companion object {
        fun factory(noteId: Long, folderId: Long?): ViewModelProvider.Factory =
            appViewModelFactory { db, app ->
                NoteEditorViewModel(NotesRepository(db, app), db.tagsDao(), app, noteId, folderId)
            }
    }
}
