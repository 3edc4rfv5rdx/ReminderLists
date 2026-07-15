package com.reminderlists.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.util.Limits

// Shared dialogs (TZ 8, no duplication): used by Lists now, reused by Notes later.

// Name + optional comment editor — create/edit folder or list/note.
@Composable
fun NameCommentDialog(
    title: String,
    initialName: String,
    initialComment: String,
    onSave: (name: String, comment: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var comment by rememberSaveable { mutableStateOf(initialComment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FloatingLabelTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.field_name),
                    maxLength = Limits.NAME,
                    autoFocus = true,
                )
                FloatingLabelTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = stringResource(R.string.field_comment),
                    maxLength = Limits.COMMENT,
                    singleLine = false,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_save),
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), comment.trim()) },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// Single text field editor — rename, edit comment.
@Composable
fun EditTextDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    required: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FloatingLabelTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                maxLength = maxLength,
                singleLine = required,
                autoFocus = true,
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_save),
                enabled = !required || value.isNotBlank(),
                onClick = { onSave(value.trim()) },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// Simple confirmation (delete etc.).
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            DialogConfirmButton(confirmLabel, onConfirm, destructive = true)
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// What happens to a folder's contents when the folder is deleted (TZ 3.1 / 3.2a).
enum class FolderDeleteMode { KEEP_ALL, DELETE_UNLOCKED, DELETE_ALL }

// Folder delete confirmation with the contents choice (TZ 3.1): move contents to root or delete
// them. lockedCount > 0 (delete-protected lists inside, TZ 3.2a) adds the middle option that
// spares them; wiping them too (DELETE_ALL) is left to the caller to gate with the PIN.
@Composable
fun DeleteFolderDialog(
    folderName: String,
    onConfirm: (FolderDeleteMode) -> Unit,
    onDismiss: () -> Unit,
    lockedCount: Int = 0,
) {
    var mode by rememberSaveable { mutableStateOf(FolderDeleteMode.KEEP_ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_folder_title)) },
        text = {
            Column {
                Text(folderName)
                RadioRow(
                    text = stringResource(R.string.delete_folder_keep_lists),
                    selected = mode == FolderDeleteMode.KEEP_ALL,
                    onClick = { mode = FolderDeleteMode.KEEP_ALL },
                )
                if (lockedCount > 0) {
                    RadioRow(
                        text = pluralStringResource(
                            R.plurals.delete_folder_keep_protected,
                            lockedCount,
                            lockedCount,
                        ),
                        selected = mode == FolderDeleteMode.DELETE_UNLOCKED,
                        onClick = { mode = FolderDeleteMode.DELETE_UNLOCKED },
                    )
                }
                RadioRow(
                    text = if (lockedCount > 0) {
                        pluralStringResource(
                            R.plurals.delete_folder_delete_protected,
                            lockedCount,
                            lockedCount,
                        )
                    } else {
                        stringResource(R.string.delete_folder_delete_lists)
                    },
                    selected = mode == FolderDeleteMode.DELETE_ALL,
                    onClick = { mode = FolderDeleteMode.DELETE_ALL },
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_delete),
                onClick = { onConfirm(mode) },
                destructive = true,
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// Pick a destination folder (or root) — move list/note to folder (TZ 3.2).
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<Pair<Long?, String>>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RadioRow(
                    text = stringResource(R.string.folder_root),
                    selected = selectedId == null,
                    onClick = { onPick(null) },
                )
                folders.forEach { (id, name) ->
                    RadioRow(
                        text = name,
                        selected = selectedId == id,
                        onClick = { onPick(id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, Modifier.padding(start = 4.dp))
    }
}
