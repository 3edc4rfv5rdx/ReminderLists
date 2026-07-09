package com.reminderlists.ui.screens.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.ui.components.AppDropdownMenu
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.RowMenuButton
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.CommentFooter
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.DeleteFolderDialog
import com.reminderlists.ui.components.EditTextDialog
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.FolderRow
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.ui.components.FolderPickerDialog
import com.reminderlists.ui.components.NameCommentDialog
import com.reminderlists.ui.components.PinDialog
import com.reminderlists.ui.components.PinSetupDialog
import com.reminderlists.ui.components.RowTitle
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.util.Limits

// Which dialog is open on the Lists tab (single slot — dialogs never stack).
private sealed interface ListsDialog {
    data object NewFolder : ListsDialog
    data object NewList : ListsDialog
    data class RenameFolder(val folder: FolderEntity) : ListsDialog
    data class FolderComment(val folder: FolderEntity) : ListsDialog
    data class DeleteFolder(val folder: FolderEntity) : ListsDialog
    data class EditList(val list: ListEntity) : ListsDialog
    data class MoveList(val list: ListEntity) : ListsDialog
    data class ProtectList(val list: ListEntity) : ListsDialog
    data class DeleteList(val list: ListEntity) : ListsDialog
}

// Lists tab (TZ 3.1 / 3.2): folders + lists in root, lists inside an opened folder.
@Composable
fun ListsScreen(navController: NavController, contentPadding: PaddingValues) {
    val vm: ListsViewModel = viewModel(factory = ListsViewModel.Factory)
    val folders by vm.folders.collectAsState()
    val lists by vm.lists.collectAsState()
    val currentFolder by vm.currentFolder.collectAsState()
    val itemCounts by vm.itemCounts.collectAsState()
    val folderCounts by vm.folderCounts.collectAsState()
    // Collected here so the flow is live whenever the PIN gate checks it (TZ 3.6).
    val defaultPin by vm.defaultPin.collectAsState()

    var dialog by remember { mutableStateOf<ListsDialog?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }

    // PIN gate (TZ 3.6): every operation on a protected list first asks for the PIN;
    // the pending action runs only after a correct entry.
    var pinGate by remember { mutableStateOf<Pair<ListEntity, () -> Unit>?>(null) }
    val gated: (ListEntity, () -> Unit) -> Unit = { list, action ->
        if (list.pinEnabled) pinGate = list to action else action()
    }

    val inFolder = currentFolder != null
    BackHandler(enabled = inFolder) { vm.openFolder(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            AppTopBar(
                title = currentFolder?.name ?: stringResource(R.string.tab_lists),
                onBack = if (inFolder) {
                    { vm.openFolder(null) }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    AppDropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        // New folder lives in the menu (rare action, no nesting inside a folder).
                        if (!inFolder) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fab_new_folder)) },
                                onClick = {
                                    topMenuOpen = false
                                    dialog = ListsDialog.NewFolder
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_dictionary)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.DICTIONARY) { launchSingleTop = true }
                            },
                        )
                        // TODO menu item Backup/Restore (TZ 3.8) once that feature exists.
                    }
                },
            )

            if (lists.isEmpty() && (inFolder || folders.isEmpty())) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    text = stringResource(R.string.empty_lists),
                    modifier = Modifier.weight(1f),
                )
                CommentFooter(currentFolder?.comment)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!inFolder) {
                        items(folders, key = { "folder-${it.id}" }) { folder ->
                            val listCount = folderCounts[folder.id] ?: 0
                            FolderRow(
                                name = folder.name,
                                comment = folder.comment,
                                // No counter for an empty folder; non-empty is bold (TZ 3.1).
                                countsText = if (listCount > 0) "($listCount)" else null,
                                bold = listCount > 0,
                                onOpen = { vm.openFolder(folder) },
                                onRename = { dialog = ListsDialog.RenameFolder(folder) },
                                onEditComment = { dialog = ListsDialog.FolderComment(folder) },
                                onDelete = { dialog = ListsDialog.DeleteFolder(folder) },
                            )
                        }
                    }
                    items(lists, key = { "list-${it.id}" }) { list ->
                        val counts = itemCounts[list.id]
                        val total = counts?.total ?: 0
                        val done = counts?.done ?: 0
                        val allDone = total > 0 && done == total
                        ListRow(
                            list = list,
                            // No counter for an empty list; in-progress is bold, fully done is
                            // regular weight with strikethrough (TZ 3.2).
                            countsText = if (total > 0) "($done/$total)" else null,
                            fontWeight = if (total > 0 && !allDone) FontWeight.Bold else null,
                            textDecoration = if (allDone) TextDecoration.LineThrough else null,
                            onOpen = {
                                gated(list) {
                                    navController.navigate(Routes.listDetail(list.id)) { launchSingleTop = true }
                                }
                            },
                            onEdit = { gated(list) { dialog = ListsDialog.EditList(list) } },
                            onMove = { gated(list) { dialog = ListsDialog.MoveList(list) } },
                            // Protect opens the PIN setup; Unprotect (gated) just turns it off.
                            onProtect = {
                                if (list.pinEnabled) {
                                    gated(list) { vm.setProtection(list, enabled = false, customPin = null) }
                                } else {
                                    dialog = ListsDialog.ProtectList(list)
                                }
                            },
                            onDelete = { gated(list) { dialog = ListsDialog.DeleteList(list) } },
                        )
                    }
                    // Folder comment goes at the bottom, after the lists and a divider (user rule).
                    val folderComment = currentFolder?.comment
                    if (inFolder && folderComment != null) {
                        item(key = "folder-comment") { CommentFooter(folderComment) }
                    }
                }
            }
        }

        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_list),
            onClick = { dialog = ListsDialog.NewList },
            // Fixed FAB level from the window bottom (TZ 8) — independent of the bottom bar.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )
    }

    when (val d = dialog) {
        null -> {}

        ListsDialog.NewFolder -> NameCommentDialog(
            title = stringResource(R.string.fab_new_folder),
            initialName = "",
            initialComment = "",
            onSave = { name, comment ->
                vm.createFolder(name, comment)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        ListsDialog.NewList -> NameCommentDialog(
            title = stringResource(R.string.fab_new_list),
            initialName = "",
            initialComment = "",
            onSave = { name, comment ->
                vm.createList(name, comment)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.RenameFolder -> EditTextDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.field_name),
            initial = d.folder.name,
            maxLength = Limits.NAME,
            required = true,
            onSave = { name ->
                vm.renameFolder(d.folder, name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.FolderComment -> EditTextDialog(
            title = stringResource(R.string.action_edit_comment),
            label = stringResource(R.string.field_comment),
            initial = d.folder.comment.orEmpty(),
            maxLength = Limits.COMMENT,
            required = false,
            onSave = { comment ->
                vm.updateFolderComment(d.folder, comment)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.DeleteFolder -> DeleteFolderDialog(
            folderName = d.folder.name,
            onConfirm = { deleteContents ->
                vm.deleteFolder(d.folder, deleteContents)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.EditList -> NameCommentDialog(
            title = stringResource(R.string.action_edit),
            initialName = d.list.name,
            initialComment = d.list.comment.orEmpty(),
            onSave = { name, comment ->
                vm.editList(d.list, name, comment)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.MoveList -> FolderPickerDialog(
            title = stringResource(R.string.action_move_to_folder),
            folders = folders.map { it.id to it.name },
            selectedId = d.list.folderId,
            onPick = { folderId ->
                vm.moveList(d.list, folderId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is ListsDialog.ProtectList -> {
            val hasDefaultPin = !defaultPin.isNullOrEmpty()
            PinSetupDialog(
                title = stringResource(R.string.action_protect),
                emptyPinAllowed = hasDefaultPin,
                emptyPinHint = stringResource(
                    if (hasDefaultPin) R.string.pin_empty_default else R.string.pin_no_default,
                ),
                onSave = { pin ->
                    vm.setProtection(d.list, enabled = true, customPin = pin.takeIf { it.isNotEmpty() })
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        is ListsDialog.DeleteList -> ConfirmDialog(
            title = stringResource(R.string.delete_list_title),
            text = d.list.name,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.deleteList(d.list)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }

    // PIN entry for the gated action (TZ 3.6); shown on top, runs the action on success.
    pinGate?.let { (list, action) ->
        PinDialog(
            title = list.name,
            verify = { vm.pinMatches(list, it) },
            onSuccess = {
                pinGate = null
                action()
            },
            onDismiss = { pinGate = null },
        )
    }
}

// List row (TZ 3.2): lock icon marks a PIN-protected list (TZ 3.6).
// Tap opens; «⋯» = context menu (TZ 8, no long-press on records).
// Swipe right = edit, swipe left = delete (TZ 8 §735); both go through the PIN gate.
@Composable
private fun ListRow(
    list: ListEntity,
    countsText: String?,
    fontWeight: FontWeight?,
    textDecoration: TextDecoration?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onProtect: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeActionsRow(onEdit = onEdit, onDelete = onDelete) {
        ListItem(
            headlineContent = {
                RowTitle(name = list.name, countsText = countsText, fontWeight = fontWeight, textDecoration = textDecoration)
            },
            // A protected list must not leak its comment before the PIN gate (TZ 3.6).
            supportingContent = list.comment?.takeIf { !list.pinEnabled }?.let {
                { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (list.pinEnabled) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                    RowMenuButton { dismiss ->
                        ListMenuItems(dismiss, list.pinEnabled, onEdit, onMove, onProtect, onDelete)
                    }
                }
            },
            modifier = Modifier.clickable { onOpen() },
        )
    }
}

@Composable
private fun ListMenuItems(
    dismiss: () -> Unit,
    isProtected: Boolean,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onProtect: () -> Unit,
    onDelete: () -> Unit,
) {
    MenuItem(R.string.action_edit) { dismiss(); onEdit() }
    MenuItem(R.string.action_move_to_folder) { dismiss(); onMove() }
    // Protect / Unprotect depending on the current state (TZ 3.6).
    MenuItem(if (isProtected) R.string.action_unprotect else R.string.action_protect) { dismiss(); onProtect() }
    MenuItem(R.string.action_delete) { dismiss(); onDelete() }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}
