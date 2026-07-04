package com.reminderlists.ui.screens.lists

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.dao.ListPickerEntry
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.components.AppDropdownMenu
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.CommentFooter
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.DialogDismissButton
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.LongPressEditDeleteBox
import com.reminderlists.ui.components.DragReorderState
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.PhotoViewerDialog
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.ui.components.rememberDragReorderState
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.ui.theme.LargeItemTextStyle
import com.reminderlists.util.Limits
import com.reminderlists.util.ShareUtils
import com.reminderlists.util.TextFormat

private const val ACTIVE_KEY_PREFIX = "a-"
private const val DONE_KEY_PREFIX = "d-"

// Move dialog item list: 7 rows of 48dp (checkbox touch target) before it scrolls (user rule).
private val MOVE_DIALOG_LIST_MAX_HEIGHT = 336.dp

// Opened list screen (TZ 3.2 / 3.3): active items, 3px divider, done items below.
@Composable
fun ListDetailScreen(navController: NavController, listId: Long) {
    val vm: ListDetailViewModel = viewModel(factory = ListDetailViewModel.factory(listId))
    val list by vm.list.collectAsState()
    val activeItems by vm.activeItems.collectAsState()
    val doneItems by vm.doneItems.collectAsState()
    val dictionaryTexts by vm.dictionaryTexts.collectAsState()
    val photoCounts by vm.photoCounts.collectAsState()
    val context = LocalContext.current

    var topMenuOpen by remember { mutableStateOf(false) }
    var shareDialogOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ItemEntity?>(null) }
    var photoViewerItem by remember { mutableStateOf<ItemEntity?>(null) }

    // Move/copy dialog (TZ 3.3): opened via the in-list menu "Move" (no long-press — user decision).
    var moveDialogOpen by remember { mutableStateOf(false) }

    // Large font / presentation mode (TZ 3.5): long-press on the FAB toggles, Back exits too.
    var largeFont by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = largeFont) { largeFont = false }

    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val activity = LocalActivity.current
    DisposableEffect(largeFont, keepScreenOn) {
        if (largeFont && keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

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
                    AppDropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        // TODO in-list menu (TZ 3.2): Comment.
                        // Items needing something to act on are disabled while the list is empty;
                        // "Delete checked" / "Uncheck all" also need at least one done item.
                        val hasItems = activeItems.isNotEmpty() || doneItems.isNotEmpty()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_move)) },
                            enabled = hasItems,
                            onClick = {
                                topMenuOpen = false
                                moveDialogOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share)) },
                            enabled = hasItems,
                            onClick = {
                                topMenuOpen = false
                                shareDialogOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete_checked)) },
                            enabled = doneItems.isNotEmpty(),
                            onClick = {
                                topMenuOpen = false
                                vm.deleteChecked()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_uncheck_all)) },
                            enabled = doneItems.isNotEmpty(),
                            onClick = {
                                topMenuOpen = false
                                vm.uncheckAll()
                            },
                        )
                    }
                },
            )
            if (activeItems.isEmpty() && doneItems.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    text = stringResource(R.string.empty_items),
                    modifier = Modifier.weight(1f),
                )
                CommentFooter(list?.comment)
            } else if (largeFont) {
                // Large font mode (TZ 3.5): view + toggle only, no editing.
                LazyColumn(Modifier.fillMaxSize()) {
                    items(activeItems, key = { ACTIVE_KEY_PREFIX + it.id }) { item ->
                        LargeFontRow(item = item, onToggle = { vm.toggleDone(item) })
                    }
                    if (doneItems.isNotEmpty()) {
                        item(key = "divider") {
                            HorizontalDivider(
                                thickness = 3.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(doneItems, key = { DONE_KEY_PREFIX + it.id }) { item ->
                        LargeFontRow(item = item, onToggle = { vm.toggleDone(item) })
                    }
                }
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
                            inDictionary = TextFormat.toDictionaryEntry(item.text, item.unit) in dictionaryTexts,
                            photoCount = photoCounts[item.id] ?: 0,
                            onToggle = { vm.toggleDone(item) },
                            onEdit = { openEditor(item.id) },
                            onDelete = { pendingDelete = item },
                            onAddToDictionary = { vm.addToDictionary(item) },
                            onOpenPhotos = { photoViewerItem = item },
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
                            inDictionary = TextFormat.toDictionaryEntry(item.text, item.unit) in dictionaryTexts,
                            photoCount = photoCounts[item.id] ?: 0,
                            onToggle = { vm.toggleDone(item) },
                            onEdit = { openEditor(item.id) },
                            onDelete = { pendingDelete = item },
                            onAddToDictionary = { vm.addToDictionary(item) },
                            onOpenPhotos = { photoViewerItem = item },
                        )
                    }
                    // List comment at the bottom, after the items and a divider (user rule).
                    val listComment = list?.comment
                    if (listComment != null) {
                        item(key = "list-comment") { CommentFooter(listComment) }
                    }
                }
            }
        }
        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_item),
            // No editing in large font mode (TZ 3.5) — a tap does nothing there.
            onClick = { if (!largeFont) openEditor(0) },
            onLongClick = { largeFont = !largeFont },
            // Fixed FAB level from the window bottom (TZ 8) — same as on tab screens.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )
    }

    // Move/copy items to another list (TZ 3.3): one dialog with item selection,
    // a Copy checkbox and a target list dropdown.
    if (moveDialogOpen) {
        val moveTargets by vm.moveTargets.collectAsState()
        MoveItemsDialog(
            items = activeItems + doneItems,
            targets = moveTargets,
            onConfirm = { itemIds, targetListId, copy ->
                vm.moveItemsTo(itemIds, targetListId, copy)
                moveDialogOpen = false
            },
            onDismiss = { moveDialogOpen = false },
        )
    }

    // Share the list (TZ 3.7): choose all items or only unfinished, then hand off to Share Intent.
    if (shareDialogOpen) {
        ShareChoiceDialog(
            onPick = { onlyUnfinished ->
                shareDialogOpen = false
                ShareUtils.shareText(context, vm.buildShareText(onlyUnfinished))
            },
            onDismiss = { shareDialogOpen = false },
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

    // Fullscreen photo viewer for an item (TZ 3.3 p.3): view, add more, delete.
    photoViewerItem?.let { item ->
        val photos by remember(item.id) { vm.itemPhotos(item.id) }.collectAsState(initial = null)
        val loaded = photos
        if (loaded != null) {
            PhotoViewerDialog(
                files = loaded.map { PhotoManager.fileFor(context, it.filePath) },
                canAdd = loaded.size < Limits.MAX_PHOTOS,
                onPicked = { uri -> vm.addPhoto(item, uri) },
                onDelete = { index -> loaded.getOrNull(index)?.let(vm::deletePhoto) },
                onDismiss = { photoViewerItem = null },
            )
        }
    }
}

// Share choice (TZ 3.7): all items or only unfinished (done items excluded).
@Composable
private fun ShareChoiceDialog(onPick: (onlyUnfinished: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_dialog_title)) },
        text = {
            Column {
                ShareChoiceRow(stringResource(R.string.share_all)) { onPick(false) }
                ShareChoiceRow(stringResource(R.string.share_unfinished)) { onPick(true) }
            }
        },
        confirmButton = {},
        dismissButton = { DialogDismissButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

@Composable
private fun ShareChoiceRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

// Item text + "5/kg" amount, strikethrough when done — shared by the normal and the
// move-selection rows (text, quantity and unit: one size, one color — user rule).
@Composable
private fun RowScope.ItemTexts(item: ItemEntity) {
    val textColor = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val decoration = if (item.isDone) TextDecoration.LineThrough else null
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = decoration,
        color = textColor,
        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
    )
    val amount = TextFormat.formatAmount(item.quantity, item.unit)
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
}

// Move/copy dialog (TZ 3.3): item selection with checkboxes, a Copy checkbox below,
// then a dropdown with the destination lists; OK confirms, Cancel dismisses.
@Composable
private fun MoveItemsDialog(
    items: List<ItemEntity>,
    targets: List<ListPickerEntry>,
    onConfirm: (itemIds: Set<Long>, targetListId: Long, copy: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var copy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ListPickerEntry?>(null) }
    var targetMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_dialog_title)) },
        text = {
            Column {
                // Items to move — capped at ~7 rows (user rule), scrollable beyond that.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = MOVE_DIALOG_LIST_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEach { item ->
                        val checked = item.id in selectedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - item.id else selectedIds + item.id
                                },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedIds = if (checked) selectedIds - item.id else selectedIds + item.id
                                },
                            )
                            ItemTexts(item)
                        }
                    }
                }
                // Copy switch — a toggle, not a checkbox, so it does not blend into the item list.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copy = !copy }
                        .padding(vertical = 8.dp),
                ) {
                    Switch(checked = copy, onCheckedChange = { copy = it })
                    Text(stringResource(R.string.move_copy), Modifier.padding(start = 12.dp))
                }
                // Destination list dropdown, "Folder / Name" labels, root lists first.
                if (targets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.move_no_targets),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { targetMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = target?.let(::pickerLabel) ?: stringResource(R.string.move_select_list),
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        AppDropdownMenu(
                            expanded = targetMenuOpen,
                            onDismissRequest = { targetMenuOpen = false },
                        ) {
                            targets.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(pickerLabel(entry)) },
                                    onClick = {
                                        target = entry
                                        targetMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = target
            DialogConfirmButton(
                text = stringResource(R.string.action_ok),
                enabled = selectedIds.isNotEmpty() && chosen != null,
                onClick = { chosen?.let { onConfirm(selectedIds, it.id, copy) } },
            )
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

private fun pickerLabel(entry: ListPickerEntry): String =
    entry.folderName?.let { "$it / ${entry.name}" } ?: entry.name

// Large font mode row (TZ 3.5): thick bullet dot + huge text, tap toggles done, nothing else.
@Composable
private fun LargeFontRow(item: ItemEntity, onToggle: () -> Unit) {
    val amount = TextFormat.formatAmount(item.quantity, item.unit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(
                    color = if (item.isDone) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = if (amount.isEmpty()) item.text else "${item.text} $amount",
            style = LargeItemTextStyle,
            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp),
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
    photoCount: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToDictionary: () -> Unit,
    onOpenPhotos: () -> Unit,
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
        ItemRowContent(
            item, dragState, rowKey, inDictionary, photoCount,
            onToggle, onEdit, onDelete, onAddToDictionary, onOpenPhotos,
        )
    }
}

@Composable
private fun ItemRowContent(
    item: ItemEntity,
    dragState: DragReorderState?,
    rowKey: String,
    inDictionary: Boolean,
    photoCount: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToDictionary: () -> Unit,
    onOpenPhotos: () -> Unit,
) {
    // Long-press = edit/delete menu at the touch point (TZ 8); short tap is reserved (TZ 3.3).
    LongPressEditDeleteBox(onEdit = onEdit, onDelete = onDelete) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, end = 16.dp),
        ) {
            Checkbox(checked = item.isDone, onCheckedChange = { onToggle() })
            ItemTexts(item)
            if (photoCount > 0) {
                // Photo icon only when the item already has photos (TZ 3.3 p.3).
                IconButton(onClick = onOpenPhotos) {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = stringResource(R.string.action_photos),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
                    modifier = Modifier.padding(start = 12.dp).pointerInput(rowKey) {
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
}
