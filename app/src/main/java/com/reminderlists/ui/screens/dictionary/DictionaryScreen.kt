package com.reminderlists.ui.screens.dictionary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.entity.DictionaryEntity
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.EditTextDialog
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.util.Limits

private sealed interface DictDialog {
    data object New : DictDialog
    data class Edit(val entry: DictionaryEntity) : DictDialog
    data class Delete(val entry: DictionaryEntity) : DictDialog
}

// Dictionary management screen (TZ 3.4): alphabetical entries, add/edit/delete.
@Composable
fun DictionaryScreen(navController: NavController) {
    val vm: DictionaryViewModel = viewModel(factory = DictionaryViewModel.Factory)
    val entries by vm.entries.collectAsState()
    var dialog by remember { mutableStateOf<DictDialog?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            AppTopBar(
                title = stringResource(R.string.menu_dictionary),
                onBack = { navController.popBackStack() },
            )
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    text = stringResource(R.string.empty_dictionary),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { entry ->
                        SwipeActionsRow(
                            onEdit = { dialog = DictDialog.Edit(entry) },
                            onDelete = { dialog = DictDialog.Delete(entry) },
                        ) {
                            DictionaryRow(
                                entry = entry,
                                onEdit = { dialog = DictDialog.Edit(entry) },
                                onDelete = { dialog = DictDialog.Delete(entry) },
                            )
                        }
                    }
                }
            }
        }
        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.dictionary_new_entry),
            onClick = { dialog = DictDialog.New },
            // Fixed FAB level from the window bottom (TZ 8).
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )
    }

    when (val d = dialog) {
        null -> {}

        DictDialog.New -> EditTextDialog(
            title = stringResource(R.string.dictionary_new_entry),
            label = stringResource(R.string.field_name),
            initial = "",
            maxLength = Limits.ITEM_TEXT,
            required = true,
            onSave = { text ->
                vm.add(text)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is DictDialog.Edit -> EditTextDialog(
            title = stringResource(R.string.action_edit),
            label = stringResource(R.string.field_name),
            initial = d.entry.text,
            maxLength = Limits.ITEM_TEXT,
            required = true,
            onSave = { text ->
                vm.rename(d.entry, text)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is DictDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_entry_title),
            text = d.entry.text,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.delete(d.entry)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

// Entry row: long-press = edit/delete menu, swipes handled by SwipeActionsRow (TZ 8).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictionaryRow(
    entry: DictionaryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            onClick = {
                menuOpen = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                menuOpen = false
                onDelete()
            },
        )
    }
    Text(
        text = entry.text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
