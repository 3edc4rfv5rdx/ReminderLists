package com.reminderlists.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// Opened list (TZ 3.2). Items, selection mode and large-font mode arrive with TZ 3.3 / 3.5.
class ListDetailViewModel(repo: ListsRepository, listId: Long) : ViewModel() {

    val list: StateFlow<ListEntity?> =
        repo.observeList(listId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        fun factory(listId: Long): ViewModelProvider.Factory =
            appViewModelFactory { db -> ListDetailViewModel(ListsRepository(db), listId) }
    }
}
