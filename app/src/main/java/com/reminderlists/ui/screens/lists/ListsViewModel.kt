package com.reminderlists.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.dao.ListItemCounts
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Lists tab state (TZ 3.1 / 3.2): root shows folders + root lists; opening a folder is
// in-tab state (bottom bar stays, tab state is preserved on tab switch).
@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModel(private val repo: ListsRepository) : ViewModel() {

    private val currentFolderId = MutableStateFlow<Long?>(null)

    val folders: StateFlow<List<FolderEntity>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Folder currently opened; null = root. Falls back to root if the folder disappears.
    val currentFolder: StateFlow<FolderEntity?> =
        combine(currentFolderId, folders) { id, all -> all.find { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lists: StateFlow<List<ListEntity>> =
        currentFolder.flatMapLatest { repo.observeLists(it?.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // listId -> (done/total) counters shown next to list names (TZ 3.2).
    val itemCounts: StateFlow<Map<Long, ListItemCounts>> =
        repo.observeListItemCounts()
            .map { counts -> counts.associateBy { it.listId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // folderId -> lists count shown next to folder names (TZ 3.1).
    val folderCounts: StateFlow<Map<Long, Int>> =
        repo.observeFolderListCounts()
            .map { counts -> counts.associate { it.folderId to it.total } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun openFolder(folder: FolderEntity?) {
        currentFolderId.value = folder?.id
    }

    fun createFolder(name: String, comment: String?) {
        viewModelScope.launch { repo.createFolder(name, comment) }
    }

    fun renameFolder(folder: FolderEntity, name: String) {
        viewModelScope.launch { repo.renameFolder(folder, name) }
    }

    fun updateFolderComment(folder: FolderEntity, comment: String?) {
        viewModelScope.launch { repo.updateFolderComment(folder, comment) }
    }

    fun deleteFolder(folder: FolderEntity, deleteLists: Boolean) {
        viewModelScope.launch { repo.deleteFolder(folder, deleteLists) }
    }

    fun createList(name: String, comment: String?) {
        viewModelScope.launch { repo.createList(currentFolderId.value, name, comment) }
    }

    fun editList(list: ListEntity, name: String, comment: String?) {
        viewModelScope.launch { repo.editList(list, name, comment) }
    }

    fun moveList(list: ListEntity, folderId: Long?) {
        viewModelScope.launch { repo.moveList(list, folderId) }
    }

    fun deleteList(list: ListEntity) {
        viewModelScope.launch { repo.deleteList(list) }
    }

    companion object {
        val Factory = appViewModelFactory { db -> ListsViewModel(ListsRepository(db)) }
    }
}
