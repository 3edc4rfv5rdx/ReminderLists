package com.reminderlists.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

// Record-row wrapper (TZ 8): long-press opens a context menu at the touch point;
// optional tap action (open folder/list etc.).
@Composable
fun LongPressMenuBox(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier
            .onSizeChanged { heightPx = it.height }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap?.invoke() },
                    onLongPress = { position ->
                        pressOffset = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
                        menuOpen = true
                    },
                )
            },
    ) {
        content()
        AppDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // The anchor is the whole row (menu would open under it) — shift back to the finger.
            offset = DpOffset(pressOffset.x, pressOffset.y - with(density) { heightPx.toDp() }),
        ) {
            menuContent { menuOpen = false }
        }
    }
}

// Shorthand for the common Edit/Delete record menu (TZ 8).
@Composable
fun LongPressEditDeleteBox(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LongPressMenuBox(
        modifier = modifier,
        menuContent = { dismiss ->
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
        },
        content = content,
    )
}
