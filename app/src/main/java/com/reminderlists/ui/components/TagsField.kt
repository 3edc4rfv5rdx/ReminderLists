package com.reminderlists.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.reminderlists.R
import com.reminderlists.util.Limits
import com.reminderlists.util.TextFormat

// Tags input (TZ 4.2 п. 3, reused by Notes and Filters): comma-separated field +
// «#» dictionary picker + «x» clear. Normalization happens on Save (TextFormat.parseTags);
// the field itself is free text.
@Composable
fun TagsField(
    value: String,
    onValueChange: (String) -> Unit,
    allTags: List<String>,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        FloatingLabelTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.field_tags),
            maxLength = Limits.TAGS_FIELD,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { pickerOpen = true }) {
            Icon(Icons.Filled.Tag, contentDescription = stringResource(R.string.action_pick_tags))
        }
        IconButton(onClick = { onValueChange("") }) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
        }
    }
    if (pickerOpen) {
        // Seeded from the field on open; tags typed manually but absent from the dictionary
        // survive OK untouched (they are in the list, just not shown as rows).
        val selected = remember { TextFormat.parseTags(value).toMutableStateList() }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.tags_dialog_title)) },
            text = {
                if (allTags.isEmpty()) {
                    Text(stringResource(R.string.tags_empty))
                } else {
                    LazyColumn {
                        items(allTags) { tag ->
                            val checked = tag in selected
                            val toggle = { if (checked) selected.remove(tag) else selected.add(tag) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { toggle() },
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { toggle() })
                                Text(tag)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.action_ok),
                    onClick = {
                        onValueChange(selected.joinToString(", "))
                        pickerOpen = false
                    },
                )
            },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.action_cancel)) { pickerOpen = false }
            },
        )
    }
}
