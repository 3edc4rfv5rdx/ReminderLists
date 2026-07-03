package com.reminderlists.ui.screens.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.lists.DictionaryRepository
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.TextFormat
import kotlinx.coroutines.launch

// Item add/edit window state (TZ 3.3): Name (required) with dictionary autocomplete (TZ 3.4),
// Quantity, Unit.
class ItemEditorViewModel(
    private val repo: ListsRepository,
    private val dictRepo: DictionaryRepository,
    private val listId: Long,
    itemId: Long,
) : ViewModel() {

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

    init {
        if (isEdit) {
            viewModelScope.launch {
                repo.getItem(itemId)?.let { item ->
                    existing = item
                    name = item.text
                    quantity = item.quantity.orEmpty()
                    unit = item.unit.orEmpty()
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val item = existing
            if (item == null) {
                repo.addItem(listId, name.trim(), quantity, unit)
            } else {
                repo.editItem(item, name.trim(), quantity, unit)
            }
            onSaved()
        }
    }

    companion object {
        fun factory(listId: Long, itemId: Long): ViewModelProvider.Factory =
            appViewModelFactory { db ->
                ItemEditorViewModel(ListsRepository(db), DictionaryRepository(db), listId, itemId)
            }
    }
}
