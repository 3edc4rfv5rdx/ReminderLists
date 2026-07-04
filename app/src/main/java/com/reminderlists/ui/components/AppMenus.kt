package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import com.reminderlists.R

// One background token for every popup surface (TZ 8): menus and autocomplete drop-downs.
val menuContainerColor: androidx.compose.ui.graphics.Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer

// Single dropdown menu component (TZ 8): explicit themed background for every menu in the app.
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = menuContainerColor,
        content = content,
    )
}

// Per-row «⋯» button — the only way to open a record's context menu (TZ 8):
// long-press on records is not used; it is reserved for the FAB / large-font mode (TZ 3.5).
@Composable
fun RowMenuButton(
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.action_menu))
        }
        AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuContent { menuOpen = false }
        }
    }
}

// Shorthand for the common Edit/Delete record menu (TZ 8).
@Composable
fun EditDeleteMenuButton(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RowMenuButton(modifier) { dismiss ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            onClick = {
                dismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                dismiss()
                onDelete()
            },
        )
    }
}
