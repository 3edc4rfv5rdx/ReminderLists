package com.reminderlists.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.DateField
import com.reminderlists.ui.components.FloatingLabelTextField
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.PhotoStrip
import com.reminderlists.ui.components.PhotoViewerDialog
import com.reminderlists.ui.components.PriorityEditor
import com.reminderlists.ui.components.TagsField
import com.reminderlists.util.Limits

// Note add/edit form (TZ 4A.2): Title (required), Content, Date (free text), Photos, Tags,
// Priority. Reuses the reminder form components (TZ 8, «без дублирования»); no firing fields.
@Composable
fun NoteEditorScreen(navController: NavController, noteId: Long, folderId: Long?) {
    val vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.factory(noteId, folderId))
    val context = LocalContext.current
    val allTags by vm.allTags.collectAsState()

    // Publish validation snacks to the single app-wide host (TZ 8).
    val snackController = LocalSnackController.current
    LaunchedEffect(vm.snack) {
        vm.snack?.let { snackController?.show(it); vm.snack = null }
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(if (vm.isEdit) R.string.action_edit else R.string.editor_new_note),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { vm.save { navController.popBackStack() } }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
            Column(
                // Compact form: one small uniform gap between stacked fields (user rule).
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // 1–2: Title (required) + Content (TZ 4A.2).
                FloatingLabelTextField(
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    label = stringResource(R.string.field_title),
                    maxLength = Limits.NAME,
                    autoFocus = !vm.isEdit,
                )
                FloatingLabelTextField(
                    value = vm.content,
                    onValueChange = { vm.content = it },
                    label = stringResource(R.string.field_content),
                    maxLength = Limits.CONTENT,
                    singleLine = false,
                )
                // 3: Date — free text, a picker fills 'YYYY-MM-DD' for sorting/filtering (TZ 4A.2 / 4A.5).
                DateField(
                    value = vm.date,
                    onValueChange = { vm.date = it },
                    label = stringResource(R.string.field_date),
                )
                // 4: Photos — shared photo module (TZ 4A.2 п. 4 / TZ 8).
                val photoFiles = vm.photos.map { PhotoManager.fileFor(context, it.fileName) }
                PhotoStrip(
                    files = photoFiles,
                    canAdd = vm.photos.size < Limits.MAX_PHOTOS,
                    onPicked = vm::addPhoto,
                    onOpen = { index -> viewerIndex = index },
                )
                // 5: Tags with the «#» dictionary picker (TZ 4.2 п. 3).
                TagsField(
                    value = vm.tags,
                    onValueChange = { vm.tags = it },
                    allTags = allTags,
                )
                // 6: Priority «(–) ★ ★ ★ (+)» (TZ 4A.2 п. 6).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.field_priority), Modifier.weight(1f))
                    PriorityEditor(priority = vm.priority, onPriorityChange = { vm.priority = it })
                }
            }
        }
    }

    viewerIndex?.let { startIndex ->
        PhotoViewerDialog(
            files = vm.photos.map { PhotoManager.fileFor(context, it.fileName) },
            canAdd = vm.photos.size < Limits.MAX_PHOTOS,
            onPicked = vm::addPhoto,
            onDelete = vm::deletePhoto,
            onDismiss = { viewerIndex = null },
            initialPage = startIndex,
        )
    }
}
