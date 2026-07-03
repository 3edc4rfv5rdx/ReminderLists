package com.reminderlists.ui.screens.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.DragReorderState
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.ui.components.rememberDragReorderState
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.util.TextFormat

private const val ACTIVE_KEY_PREFIX = "a-"
private const val DONE_KEY_PREFIX = "d-"

// Opened list screen (TZ 3.2 / 3.3): active items, 3px divider, done items below.
@Composable
fun ListDetailScreen(navController: NavController, listId: Long) {
    val vm: ListDetailViewModel = viewModel(factory = ListDetailViewModel.factory(listId))
    val list by vm.list.collectAsState()
    val activeItems by vm.activeItems.collectAsState()
    val doneItems by vm.doneItems.collectAsState()
    val dictionaryTexts by vm.dictionaryTexts.collectAsState()

    var topMenuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ItemEntity?>(null) }

    // launchSingleTop: a swipe can fire the edit callback several times before the
    // navigation happens — without it the editor stacks up and Back "does not work".
    val openEditor: (Long) -> Unit = { itemId ->
        navController.navigate(Routes.itemEditor(listId, itemId)) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            AppTopBar(
                title = list?.name.orEmpty(),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        // TODO in-list menu (TZ 3.2): Move (multi-select), Share, Comment.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete_checked)) },
                            onClick = {
                                topMenuOpen = false
                                vm.deleteChecked()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_uncheck_all)) },
                            onClick = {
                                topMenuOpen = false
                                vm.uncheckAll()
                            },
                        )
                    }
                },
            )
            val comment = list?.comment
            if (comment != null) {
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (activeItems.isEmpty() && doneItems.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    text = stringResource(R.string.empty_items),
                )
            } else {
                val listState = rememberLazyListState()
                val dragState = rememberDragReorderState(
                    listState = listState,
                    // Only rows of the active group accept a dragged row.
                    canDragOver = { (it.key as? String)?.startsWith(ACTIVE_KEY_PREFIX) == true },
                    onMove = vm::moveActive,
                    onDrop = vm::commitReorder,
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(activeItems, key = { ACTIVE_KEY_PREFIX + it.id }) { item ->
                        ItemRow(
                            item = item,
                            dragState = dragState,
                            rowKey = ACTIVE_KEY_PREFIX + item.id,
                            inDictionary = TextFormat.toDictionaryForm(item.text) in dictionaryTexts,
                            onToggle = { vm.toggleDone(item) },
                            onEdit = { openEditor(item.id) },
                            onDelete = { pendingDelete = item },
                            onAddToDictionary = { vm.addToDictionary(item) },
                        )
                    }
                    if (doneItems.isNotEmpty()) {
                        item(key = "divider") {
                            // Divider between active and done groups (TZ 3.3, 2–4 px).
                            HorizontalDivider(
                                thickness = 3.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(doneItems, key = { DONE_KEY_PREFIX + it.id }) { item ->
                        ItemRow(
                            item = item,
                            dragState = null,
                            rowKey = DONE_KEY_PREFIX + item.id,
                            inDictionary = TextFormat.toDictionaryForm(item.text) in dictionaryTexts,
                            onToggle = { vm.toggleDone(item) },
                            onEdit = { openEditor(item.id) },
                            onDelete = { pendingDelete = item },
                            onAddToDictionary = { vm.addToDictionary(item) },
                        )
                    }
                }
            }
        }
        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_item),
            onClick = { openEditor(0) },
            // TODO long-press = large font mode (TZ 3.5).
            // Fixed FAB level from the window bottom (TZ 8) — same as on tab screens.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_item_title),
            text = item.text,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.deleteItem(item)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// Item row (TZ 3.3): checkbox · text (strikethrough when done) · "to dictionary" button
// (only while the text is not in the dictionary) · drag handle (active only).
// Long-press = edit/delete menu, swipe right = edit, swipe left = delete (TZ 8).
// Photo icon arrives with the shared photo module (TZ 3.3 p.3).
@Composable
private fun ItemRow(
    item: ItemEntity,
    dragState: DragReorderState?,
    rowKey: String,
    inDictionary: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToDictionary: () -> Unit,
) {
    val isDragging = dragState?.draggingKey == rowKey
    SwipeActionsRow(
        onEdit = onEdit,
        onDelete = onDelete,
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            // isDragging == true implies dragState != null (K2 smart cast through the local val).
            .graphicsLayer { translationY = if (isDragging) dragState.draggingOffset else 0f },
    ) {
        ItemRowContent(item, dragState, rowKey, inDictionary, onToggle, onEdit, onDelete, onAddToDictionary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRowContent(
    item: ItemEntity,
    dragState: DragReorderState?,
    rowKey: String,
    inDictionary: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToDictionary: () -> Unit,
) {
    // Long-press = edit/delete menu (user rule); short tap is reserved (multi-select, TZ 3.3).
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .padding(start = 8.dp, end = 16.dp),
    ) {
        // Text, quantity and unit: one size, one color (user rule).
        val textColor = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        val decoration = if (item.isDone) TextDecoration.LineThrough else null
        Checkbox(checked = item.isDone, onCheckedChange = { onToggle() })
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = decoration,
            color = textColor,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        val amount = listOfNotNull(item.quantity, item.unit).joinToString(" ")
        if (amount.isNotEmpty()) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = decoration,
                color = textColor,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (!inDictionary) {
            // "To dictionary": saves the formatted text as a dictionary entry (TZ 3.3 p.4).
            IconButton(onClick = onAddToDictionary) {
                Icon(
                    imageVector = Icons.Filled.BookmarkAdd,
                    contentDescription = stringResource(R.string.action_add_to_dictionary),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (dragState != null) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.pointerInput(rowKey) {
                    detectDragGestures(
                        onDragStart = { dragState.start(rowKey) },
                        onDrag = { change, amount ->
                            change.consume()
                            dragState.drag(amount.y)
                        },
                        onDragEnd = { dragState.drop() },
                        onDragCancel = { dragState.drop() },
                    )
                },
            )
        }
    }
}
