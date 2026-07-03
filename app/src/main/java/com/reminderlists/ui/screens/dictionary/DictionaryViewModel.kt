package com.reminderlists.ui.screens.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.DictionaryEntity
import com.reminderlists.data.lists.DictionaryRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Dictionary management screen state (TZ 3.4).
class DictionaryViewModel(private val repo: DictionaryRepository) : ViewModel() {

    val entries: StateFlow<List<DictionaryEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(text: String) {
        viewModelScope.launch { repo.add(text) }
    }

    fun rename(entry: DictionaryEntity, text: String) {
        viewModelScope.launch { repo.rename(entry, text) }
    }

    fun delete(entry: DictionaryEntity) {
        viewModelScope.launch { repo.delete(entry) }
    }

    companion object {
        val Factory = appViewModelFactory { db -> DictionaryViewModel(DictionaryRepository(db)) }
    }
}
