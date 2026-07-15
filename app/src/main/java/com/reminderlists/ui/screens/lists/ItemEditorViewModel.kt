package com.reminderlists.ui.screens.lists

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ItemPhotoEntity
import com.reminderlists.data.lists.DictionaryRepository
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.Limits
import com.reminderlists.util.TextFormat
import kotlinx.coroutines.launch

// One photo in the editor: existing rows carry the DB entity, new ones only the file name
// (attached to the item on Save; the file is already imported into photos/).
data class EditorPhoto(val entity: ItemPhotoEntity?, val fileName: String)

// Item add/edit window state (TZ 3.3): Name (required) with dictionary autocomplete (TZ 3.4),
// Quantity, Unit, photos (shared photo module, TZ 8).
class ItemEditorViewModel(
    private val repo: ListsRepository,
    private val dictRepo: DictionaryRepository,
    application: Application,
    private val listId: Long,
    itemId: Long,
) : ViewModel() {

    private val appContext = application.applicationContext

    var name by mutableStateOf("")
        private set
    var quantity by mutableStateOf("")
    var unit by mutableStateOf("")

    // Dictionary suggestions for the typed Name prefix (TZ 3.4): from 2 characters,
    // the exact current form is not suggested back.
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    fun onNameChange(value: String) {
        name = value
        viewModelScope.launch {
            val formatted = TextFormat.toDictionaryForm(value)
            suggestions = dictRepo.suggest(value).filter { it != formatted }
        }
    }

    fun pickSuggestion(text: String) {
        // Entry may carry a unit suffix ("Молоко /kg") — fill both fields (TZ 3.4).
        val (pickedName, pickedUnit) = TextFormat.splitDictionaryEntry(text)
        // Entries are stored capitalized ("Xxxx"); enforce the form on insert anyway.
        name = TextFormat.toDictionaryForm(pickedName)
        if (pickedUnit != null) unit = pickedUnit
        suggestions = emptyList()
    }

    val isEdit = itemId > 0
    private var existing: ItemEntity? = null

    // Photos shown in the editor; up to Limits.MAX_PHOTOS (TZ 3.3).
    val photos = mutableStateListOf<EditorPhoto>()
    private var saved = false

    init {
        if (isEdit) {
            viewModelScope.launch {
                repo.getItem(itemId)?.let { item ->
                    existing = item
                    name = item.text
                    quantity = item.quantity.orEmpty()
                    unit = item.unit.orEmpty()
                }
                repo.getItemPhotos(itemId).forEach { photos += EditorPhoto(it, it.filePath) }
            }
        }
    }

    fun addPhoto(source: Uri) {
        if (photos.size >= Limits.MAX_PHOTOS) return
        viewModelScope.launch {
            val fileName = PhotoManager.importPhoto(appContext, source) ?: return@launch
            photos += EditorPhoto(null, fileName)
        }
    }

    fun deletePhoto(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        viewModelScope.launch {
            if (photo.entity != null) {
                repo.deleteItemPhoto(photo.entity)
            } else {
                PhotoManager.delete(appContext, photo.fileName)
            }
            photos.remove(photo)
        }
    }

    fun save(onSaved: () -> Unit) {
        // Save before the edit target finished loading would insert a duplicate — ignore the tap.
        if (isEdit && existing == null) return
        viewModelScope.launch {
            val item = existing
            val targetId = if (item == null) {
                repo.addItem(listId, name.trim(), quantity, unit)
            } else {
                repo.editItem(item, name.trim(), quantity, unit)
                item.id
            }
            photos.filter { it.entity == null }.forEach { repo.addItemPhoto(targetId, it.fileName) }
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
        fun factory(listId: Long, itemId: Long): ViewModelProvider.Factory =
            appViewModelFactory { db, app ->
                ItemEditorViewModel(ListsRepository(db, app), DictionaryRepository(db), app, listId, itemId)
            }
    }
}
