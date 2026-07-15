package com.reminderlists.ui.screens.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.dao.NoteWithDetails
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.filter.FilterIndicator
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.components.AppDropdownMenu
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.CommentFooter
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.DeleteFolderDialog
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.DialogDismissButton
import com.reminderlists.ui.components.EditTextDialog
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.FilterIndicatorBadge
import com.reminderlists.ui.components.FolderDeleteMode
import com.reminderlists.ui.components.FolderPickerDialog
import com.reminderlists.ui.components.FolderRow
import com.reminderlists.ui.components.NameCommentDialog
import com.reminderlists.ui.components.PhotoViewerDialog
import com.reminderlists.ui.components.PinDialog
import com.reminderlists.ui.components.PinSetupDialog
import com.reminderlists.ui.components.PriorityStars
import com.reminderlists.ui.components.RowMenuButton
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.util.Limits
import com.reminderlists.util.ShareUtils

// Which dialog is open on the Notes tab (single slot — dialogs never stack).
private sealed interface NotesDialog {
    data object NewFolder : NotesDialog
    data class RenameFolder(val folder: NoteFolderEntity) : NotesDialog
    data class FolderComment(val folder: NoteFolderEntity) : NotesDialog
    data class DeleteFolder(val folder: NoteFolderEntity) : NotesDialog
    data class MoveNote(val note: NoteEntity) : NotesDialog
    data class ProtectNote(val note: NoteEntity) : NotesDialog
    data class DeleteNote(val note: NoteEntity) : NotesDialog
}

// Notes tab (TZ 4A): folders + root note cards, no firing. Устроен как Lists, с полями
// напоминания; свой фильтр и индикатор (TZ 4A.5 / 3.9).
@Composable
fun NotesScreen(navController: NavController, contentPadding: PaddingValues) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory)
    val context = LocalContext.current
    val folders by vm.folders.collectAsState()
    val notes by vm.notes.collectAsState()
    val currentFolder by vm.currentFolder.collectAsState()
    val folderCounts by vm.folderCounts.collectAsState()
    val filter by vm.filter.collectAsState()
    // Collected here so the flow is live whenever the PIN gate checks it (TZ 4A.4).
    val defaultPin by vm.defaultPin.collectAsState()

    var dialog by remember { mutableStateOf<NotesDialog?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }
    // Note open in the read-only viewer dialog (TZ 4A.3); null = closed.
    var viewerNoteId by remember { mutableStateOf<Long?>(null) }
    // Note whose photos are open in the fullscreen photo viewer; null = closed.
    var photoNoteId by remember { mutableStateOf<Long?>(null) }

    // PIN gate (TZ 4A.4 / 3.6): every operation on a protected note first asks for the PIN;
    // the pending action runs only after a correct entry.
    var pinGate by remember { mutableStateOf<Pair<NoteEntity, () -> Unit>?>(null) }
    val gated: (NoteEntity, () -> Unit) -> Unit = { note, action ->
        if (note.pinEnabled) pinGate = note to action else action()
    }

    val inFolder = currentFolder != null
    BackHandler(enabled = inFolder) { vm.openFolder(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            AppTopBar(
                title = currentFolder?.name ?: stringResource(R.string.tab_notes),
                onBack = if (inFolder) {
                    { vm.openFolder(null) }
                } else {
                    null
                },
                actions = {
                    // Filter indicator All/T/F/TF (TZ 3.9 / 4A.5); when active it re-opens the
                    // filter behind it.
                    FilterIndicatorBadge(
                        indicator = filter.indicator,
                        onClick = when (filter.indicator) {
                            FilterIndicator.T -> {
                                { navController.navigate(Routes.tagFilter(FilterTab.NOTES)) { launchSingleTop = true } }
                            }
                            FilterIndicator.F, FilterIndicator.TF -> {
                                { navController.navigate(Routes.filters(FilterTab.NOTES)) { launchSingleTop = true } }
                            }
                            FilterIndicator.ALL -> null
                        },
                    )
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
                                    dialog = NotesDialog.NewFolder
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_filters)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.filters(FilterTab.NOTES)) { launchSingleTop = true }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_tag_filter)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.tagFilter(FilterTab.NOTES)) { launchSingleTop = true }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_clear_filters)) },
                            onClick = {
                                topMenuOpen = false
                                vm.clearFilters()
                            },
                        )
                    }
                },
            )

            if (notes.isEmpty() && (inFolder || folders.isEmpty())) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    text = stringResource(R.string.empty_notes),
                    modifier = Modifier.weight(1f),
                )
                CommentFooter(currentFolder?.comment)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!inFolder) {
                        // With a filter active, empty folders are hidden to cut clutter (TZ 4A.5 / 3.9).
                        val visibleFolders = if (filter.isActive) {
                            folders.filter { (folderCounts[it.id] ?: 0) > 0 }
                        } else {
                            folders
                        }
                        items(visibleFolders, key = { "folder-${it.id}" }) { folder ->
                            val noteCount = folderCounts[folder.id] ?: 0
                            FolderRow(
                                name = folder.name,
                                comment = folder.comment,
                                // No counter for an empty folder; non-empty is bold (TZ 4A.1).
                                countsText = if (noteCount > 0) "($noteCount)" else null,
                                bold = noteCount > 0,
                                onOpen = { vm.openFolder(folder) },
                                onRename = { dialog = NotesDialog.RenameFolder(folder) },
                                onEditComment = { dialog = NotesDialog.FolderComment(folder) },
                                onDelete = { dialog = NotesDialog.DeleteFolder(folder) },
                            )
                        }
                    }
                    itemsIndexed(notes, key = { _, it -> "note-${it.note.id}" }) { index, detail ->
                        val note = detail.note
                        // Divider only between cards — none before the first (no stray line under
                        // the bar / after folders) and none after the last, so a folder comment's
                        // own divider never doubles up (TZ 3.1).
                        if (index > 0) HorizontalDivider()
                        NoteCard(
                            detail = detail,
                            // A protected note opens the read-only viewer behind the PIN gate;
                            // an open note taps straight into its photos (TZ 4A.3 / 4A.4). An open
                            // note with no photos has nothing to reveal — its body is on the card.
                            onOpen = {
                                if (note.pinEnabled) {
                                    gated(note) { viewerNoteId = note.id }
                                } else if (detail.photos.isNotEmpty()) {
                                    photoNoteId = note.id
                                }
                            },
                            onEdit = {
                                gated(note) {
                                    navController.navigate(Routes.noteEditor(noteId = note.id)) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onMove = { gated(note) { dialog = NotesDialog.MoveNote(note) } },
                            // Protect opens the PIN setup; Unprotect (gated) just turns it off.
                            onProtect = {
                                if (note.pinEnabled) {
                                    gated(note) { vm.setProtection(note, enabled = false, customPin = null) }
                                } else {
                                    dialog = NotesDialog.ProtectNote(note)
                                }
                            },
                            onShare = {
                                gated(note) {
                                    ShareUtils.shareText(
                                        context,
                                        listOfNotNull(note.title, note.content).joinToString("\n"),
                                    )
                                }
                            },
                            onDelete = { gated(note) { dialog = NotesDialog.DeleteNote(note) } },
                        )
                    }
                    val folderComment = currentFolder?.comment
                    if (inFolder && folderComment != null) {
                        item(key = "folder-comment") { CommentFooter(folderComment) }
                    }
                }
            }
        }

        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_note),
            // New note lands in the opened folder (root when none is open), TZ 3.9.
            onClick = {
                navController.navigate(Routes.noteEditor(folderId = currentFolder?.id)) {
                    launchSingleTop = true
                }
            },
            // Fixed FAB level from the window bottom (TZ 8) — independent of the bottom bar.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )
    }

    when (val d = dialog) {
        null -> {}

        NotesDialog.NewFolder -> NameCommentDialog(
            title = stringResource(R.string.fab_new_folder),
            initialName = "",
            initialComment = "",
            onSave = { name, comment ->
                vm.createFolder(name, comment)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is NotesDialog.RenameFolder -> EditTextDialog(
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

        is NotesDialog.FolderComment -> EditTextDialog(
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

        is NotesDialog.DeleteFolder -> DeleteFolderDialog(
            folderName = d.folder.name,
            // Notes have no delete protection (TZ 3.2a is Lists-only), so the dialog keeps
            // its two options and DELETE_UNLOCKED never appears here.
            onConfirm = { mode ->
                vm.deleteFolder(d.folder, deleteNotes = mode != FolderDeleteMode.KEEP_ALL)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is NotesDialog.MoveNote -> FolderPickerDialog(
            title = stringResource(R.string.action_move_to_folder),
            folders = folders.map { it.id to it.name },
            selectedId = d.note.folderId,
            onPick = { folderId ->
                vm.moveNote(d.note, folderId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is NotesDialog.ProtectNote -> {
            val hasDefaultPin = !defaultPin.isNullOrEmpty()
            PinSetupDialog(
                title = stringResource(R.string.action_protect),
                emptyPinAllowed = hasDefaultPin,
                emptyPinHint = stringResource(
                    if (hasDefaultPin) R.string.pin_empty_default else R.string.pin_no_default,
                ),
                onSave = { pin ->
                    vm.setProtection(d.note, enabled = true, customPin = pin.takeIf { it.isNotEmpty() })
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        is NotesDialog.DeleteNote -> ConfirmDialog(
            title = stringResource(R.string.delete_note_title),
            text = d.note.title,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.delete(d.note)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }

    // Read-only note viewer (TZ 4A.3): opened by tapping a card (gated for a protected note).
    // Reads the live note; a Photos button hands off to the fullscreen photo viewer.
    viewerNoteId?.let { id ->
        notes.find { it.note.id == id }?.let { detail ->
            NoteViewerDialog(
                detail = detail,
                onViewPhotos = { photoNoteId = id },
                onEdit = {
                    viewerNoteId = null
                    navController.navigate(Routes.noteEditor(noteId = id)) { launchSingleTop = true }
                },
                onDismiss = { viewerNoteId = null },
            )
        }
    }

    // Photos opened from the note viewer (TZ 4A.3). Reads the live note, so an add/delete
    // inside the viewer refreshes it; if the note disappears the viewer just closes.
    photoNoteId?.let { id ->
        notes.find { it.note.id == id }?.let { detail ->
            PhotoViewerDialog(
                files = detail.photos.map { PhotoManager.fileFor(context, it.filePath) },
                canAdd = detail.photos.size < Limits.MAX_PHOTOS,
                onPicked = { vm.addPhoto(id, it) },
                onDelete = { index -> detail.photos.getOrNull(index)?.let(vm::deletePhoto) },
                onDismiss = { photoNoteId = null },
            )
        }
    }

    // PIN entry for the gated action (TZ 4A.4); shown on top, runs the action on success.
    pinGate?.let { (note, action) ->
        PinDialog(
            title = note.title,
            verify = { vm.pinMatches(note, it) },
            onSuccess = {
                pinGate = null
                action()
            },
            onDismiss = { pinGate = null },
        )
    }
}

// Note card (TZ 4A.3): like a reminder card without the time/weekday line and the Active
// checkbox. «⋯» = Edit / To folder / Protect / Share / Delete; swipe right = edit, left =
// delete. A protected note shows only its Title + lock until the PIN gate opens it (TZ 4A.4).
@Composable
private fun NoteCard(
    detail: NoteWithDetails,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onProtect: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val note = detail.note
    SwipeActionsRow(onEdit = onEdit, onDelete = onDelete) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Opaque background so the swipe color never shows through.
                .background(MaterialTheme.colorScheme.surface)
                // Tap opens the read-only viewer (gated for a protected note, TZ 4A.4);
                // Edit stays on the «⋯» menu / swipe right.
                .clickable { onOpen() }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            // Leading note icon (TZ 4A.3).
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (note.pinEnabled) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp).size(16.dp),
                        )
                    }
                    // Photo indicator (TZ 4A.3); hidden for a protected note so it stays behind
                    // the PIN gate (TZ 4A.4). Photos are viewed from the note viewer dialog.
                    if (detail.photos.isNotEmpty() && !note.pinEnabled) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    PriorityStars(note.priority, Modifier.padding(start = 8.dp))
                }
                // A protected note must not leak its body before the PIN gate (TZ 4A.4).
                if (!note.pinEnabled) {
                    note.content?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    // Tags above the Date line (TZ 4A.3).
                    if (detail.tags.isNotEmpty()) {
                        Text(
                            text = detail.tags.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            // Tags in plain black; italic sets the tag line apart (TZ 8).
                            color = MaterialTheme.colorScheme.onSurface,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    note.date?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            RowMenuButton { dismiss ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { dismiss(); onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move_to_folder)) },
                    onClick = { dismiss(); onMove() },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (note.pinEnabled) R.string.action_unprotect else R.string.action_protect,
                            ),
                        )
                    },
                    onClick = { dismiss(); onProtect() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_share)) },
                    onClick = { dismiss(); onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { dismiss(); onDelete() },
                )
            }
        }
    }
}

// Read-only note viewer (TZ 4A.3): a simple dialog with the note's fields and a button to open
// the photos, separate from the editor. Reached by tapping a card (gated for a protected note).
@Composable
private fun NoteViewerDialog(
    detail: NoteWithDetails,
    onViewPhotos: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val note = detail.note
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PriorityStars(note.priority, Modifier.padding(start = 8.dp))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                note.content?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                // Tags above the Date line (TZ 4A.3).
                if (detail.tags.isNotEmpty()) {
                    Text(
                        text = detail.tags.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        // Tags in plain black; italic sets the tag line apart (TZ 8).
                        color = MaterialTheme.colorScheme.onSurface,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                note.date?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (detail.photos.isNotEmpty()) {
                    // Filled colored button (TZ 8, no text-only buttons) — tertiary to set it
                    // apart from the primary/secondary dialog actions below.
                    Button(
                        onClick = onViewPhotos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                        Text("  ${stringResource(R.string.action_photos)} (${detail.photos.size})")
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.action_ok), onDismiss)
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_edit), onEdit)
        },
    )
}
