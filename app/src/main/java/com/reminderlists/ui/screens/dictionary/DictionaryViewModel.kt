package com.reminderlists.ui.screens.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.DictionaryEntity
import com.reminderlists.data.lists.DictionaryRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Dictionary management screen state (TZ 3.4).
class DictionaryViewModel(private val repo: DictionaryRepository) : ViewModel() {

    // Search filter (TZ 3.4): case-insensitive substring match from the first typed letters.
    val query = MutableStateFlow("")

    val entries: StateFlow<List<DictionaryEntity>> =
        combine(repo.observeAll(), query) { all, q ->
            val needle = q.trim().lowercase()
            if (needle.isEmpty()) all else all.filter { needle in it.text.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
