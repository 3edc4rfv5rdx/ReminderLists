package com.reminderlists.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.reminderlists.R

// Shared one-level folder row for the Lists (TZ 3.1) and Notes (TZ 4A.1) tabs — identical
// behaviour and menu (Rename / Edit comment / Delete). Tap opens; «⋯» = context menu
// (TZ 8, no long-press on records). No counter when empty, bold when non-empty.
@Composable
fun FolderRow(
    name: String,
    comment: String?,
    countsText: String?,
    bold: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onEditComment: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            RowTitle(
                name = name,
                countsText = countsText,
                fontWeight = if (bold) FontWeight.Bold else null,
                textDecoration = null,
            )
        },
        supportingContent = comment?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            RowMenuButton { dismiss ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { dismiss(); onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_comment)) },
                    onClick = { dismiss(); onEditComment() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { dismiss(); onDelete() },
                )
            }
        },
        modifier = Modifier.clickable { onOpen() },
    )
}
