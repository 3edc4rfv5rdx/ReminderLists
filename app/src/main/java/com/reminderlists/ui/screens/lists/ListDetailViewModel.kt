package com.reminderlists.ui.screens.lists

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ItemPhotoEntity
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.data.lists.DictionaryRepository
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.TextFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Opened list (TZ 3.2 / 3.3): active items above the divider, done items below.
// While a row is dragged, reorderOverride holds the in-progress order of the active group;
// onDrop persists it and the DB flow takes over again.
class ListDetailViewModel(
    private val repo: ListsRepository,
    private val dictRepo: DictionaryRepository,
    application: Application,
    private val listId: Long,
) : ViewModel() {

    private val appContext = application.applicationContext

    val list: StateFlow<ListEntity?> =
        repo.observeList(listId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Formatted dictionary texts — drives the "to dictionary" button visibility (TZ 3.3 p.4).
    val dictionaryTexts: StateFlow<Set<String>> =
        dictRepo.observeAll()
            .map { entries -> entries.mapTo(mutableSetOf()) { it.text } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val dbItems = repo.observeItems(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val reorderOverride = MutableStateFlow<List<ItemEntity>?>(null)

    val activeItems: StateFlow<List<ItemEntity>> =
        combine(dbItems, reorderOverride) { db, override -> override ?: db.filter { !it.isDone } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doneItems: StateFlow<List<ItemEntity>> =
        dbItems.map { items -> items.filter { it.isDone } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDone(item: ItemEntity) {
        viewModelScope.launch { repo.setItemDone(item, !item.isDone) }
    }

    // Live swap while dragging (indexes are within the active group — it renders first).
    fun moveActive(fromIndex: Int, toIndex: Int) {
        val current = (reorderOverride.value ?: activeItems.value).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        current.add(toIndex, current.removeAt(fromIndex))
        reorderOverride.value = current
    }

    fun commitReorder() {
        val order = reorderOverride.value ?: return
        viewModelScope.launch {
            repo.reorderActiveItems(order.map { it.id })
            reorderOverride.value = null
        }
    }

    // itemId -> photo count: drives the photo icon in item rows (TZ 3.3 p.3).
    val photoCounts: StateFlow<Map<Long, Int>> =
        repo.observeItemPhotoCounts(listId)
            .map { counts -> counts.associate { it.itemId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun itemPhotos(itemId: Long): Flow<List<ItemPhotoEntity>> = repo.observeItemPhotos(itemId)

    fun addPhoto(item: ItemEntity, source: Uri) {
        viewModelScope.launch {
            val fileName = PhotoManager.importPhoto(appContext, source) ?: return@launch
            repo.addItemPhoto(item.id, fileName)
        }
    }

    fun deletePhoto(photo: ItemPhotoEntity) {
        viewModelScope.launch { repo.deleteItemPhoto(photo) }
    }

    // Dictionary entry includes the unit as a " /unit" suffix when present (TZ 3.4).
    fun addToDictionary(item: ItemEntity) {
        viewModelScope.launch { dictRepo.add(TextFormat.toDictionaryEntry(item.text, item.unit)) }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch { repo.deleteItem(item) }
    }

    fun deleteChecked() {
        viewModelScope.launch { repo.deleteDoneItems(listId) }
    }

    fun uncheckAll() {
        viewModelScope.launch { repo.uncheckAll(listId) }
    }

    companion object {
        fun factory(listId: Long): ViewModelProvider.Factory =
            appViewModelFactory { db, app ->
                ListDetailViewModel(ListsRepository(db, app), DictionaryRepository(db), app, listId)
            }
    }
}
