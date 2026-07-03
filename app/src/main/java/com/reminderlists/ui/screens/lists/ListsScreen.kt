package com.reminderlists.ui.screens.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
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
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppSmallFab
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.DeleteFolderDialog
import com.reminderlists.ui.components.EditTextDialog
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.FolderPickerDialog
import com.reminderlists.ui.components.NameCommentDialog
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.ui.screens.about.AboutDialog
import com.reminderlists.util.Limits

// Which dialog is open on the Lists tab (single slot — dialogs never stack).
private sealed interface ListsDialog {
    data object NewFolder : ListsDialog
    data object NewList : ListsDialog
    data object About : ListsDialog
    data class RenameFolder(val folder: FolderEntity) : ListsDialog
    data class FolderComment(val folder: FolderEntity) : ListsDialog
    data class DeleteFolder(val folder: FolderEntity) : ListsDialog
    data class EditList(val list: ListEntity) : ListsDialog
    data class MoveList(val list: ListEntity) : ListsDialog
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

    var dialog by remember { mutableStateOf<ListsDialog?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }

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
                    DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.SETTINGS)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_about)) },
                            onClick = {
                                topMenuOpen = false
                                dialog = ListsDialog.About
                            },
                        )
                        // TODO menu items Dictionary (TZ 3.4) and Backup/Restore (TZ 3.8) once those screens exist.
                    }
                },
            )

            val currentComment = currentFolder?.comment
            if (currentComment != null) {
                Text(
                    text = currentComment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (lists.isEmpty() && (inFolder || folders.isEmpty())) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    text = stringResource(R.string.empty_lists),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!inFolder) {
                        items(folders, key = { "folder-${it.id}" }) { folder ->
                            val listCount = folderCounts[folder.id] ?: 0
                            FolderRow(
                                folder = folder,
                                // No counter for an empty folder; non-empty is bold (TZ 3.1).
                                countsText = if (listCount > 0) "($listCount)" else null,
                                fontWeight = if (listCount > 0) FontWeight.Bold else null,
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
                                navController.navigate(Routes.listDetail(list.id)) { launchSingleTop = true }
                            },
                            onEdit = { dialog = ListsDialog.EditList(list) },
                            onMove = { dialog = ListsDialog.MoveList(list) },
                            onDelete = { dialog = ListsDialog.DeleteList(list) },
                        )
                    }
                }
            }
        }

        Column(
            // Fixed FAB level from the window bottom (TZ 8) — independent of the bottom bar.
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (!inFolder) {
                AppSmallFab(
                    icon = Icons.Filled.CreateNewFolder,
                    contentDescription = stringResource(R.string.fab_new_folder),
                    onClick = { dialog = ListsDialog.NewFolder },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            AppFab(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.fab_new_list),
                onClick = { dialog = ListsDialog.NewList },
            )
        }
    }

    when (val d = dialog) {
        null -> {}

        ListsDialog.About -> AboutDialog(onDismiss = { dialog = null })

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
}

// Folder row (TZ 3.1): tap opens, long-press or «⋮» shows the actions menu.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderEntity,
    countsText: String?,
    fontWeight: FontWeight?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onEditComment: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            RowTitle(name = folder.name, countsText = countsText, fontWeight = fontWeight, textDecoration = null)
        },
        supportingContent = folder.comment?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(R.string.action_rename) { menuOpen = false; onRename() }
                    MenuItem(R.string.action_edit_comment) { menuOpen = false; onEditComment() }
                    MenuItem(R.string.action_delete) { menuOpen = false; onDelete() }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    )
}

// List row (TZ 3.2): lock icon marks a PIN-protected list (TZ 3.6).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: ListEntity,
    countsText: String?,
    fontWeight: FontWeight?,
    textDecoration: TextDecoration?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            RowTitle(name = list.name, countsText = countsText, fontWeight = fontWeight, textDecoration = textDecoration)
        },
        supportingContent = list.comment?.let {
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
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(R.string.action_edit) { menuOpen = false; onEdit() }
                        MenuItem(R.string.action_move_to_folder) { menuOpen = false; onMove() }
                        // TODO "Protect" menu item (PIN on/off) — TZ 3.6, next feature.
                        MenuItem(R.string.action_delete) { menuOpen = false; onDelete() }
                    }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    )
}

// Row title: name + optional "(counts)" — one size, one color, shared weight/strikethrough (TZ 8).
@Composable
private fun RowTitle(
    name: String,
    countsText: String?,
    fontWeight: FontWeight?,
    textDecoration: TextDecoration?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = fontWeight,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (countsText != null) {
            Text(
                text = " $countsText",
                maxLines = 1,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
            )
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}
