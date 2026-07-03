package com.reminderlists.ui.screens.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.lists.ListsRepository
import com.reminderlists.ui.appViewModelFactory
import kotlinx.coroutines.launch

// Item add/edit window state (TZ 3.3): Name (required), Quantity, Unit.
// Dictionary autocomplete for Name arrives with TZ 3.4.
class ItemEditorViewModel(
    private val repo: ListsRepository,
    private val listId: Long,
    itemId: Long,
) : ViewModel() {

    var name by mutableStateOf("")
    var quantity by mutableStateOf("")
    var unit by mutableStateOf("")

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
            appViewModelFactory { db -> ItemEditorViewModel(ListsRepository(db), listId, itemId) }
    }
}
