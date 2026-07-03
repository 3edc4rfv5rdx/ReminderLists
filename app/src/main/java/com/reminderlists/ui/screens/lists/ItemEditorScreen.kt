package com.reminderlists.ui.screens.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.FloatingLabelTextField
import com.reminderlists.ui.components.PhotoStrip
import com.reminderlists.ui.components.PhotoViewerDialog
import com.reminderlists.ui.components.menuContainerColor
import com.reminderlists.util.Limits

// Item add/edit window (TZ 3.3): Name / Quantity / Unit, up-bar with Back and Save.
// Photo button (shared photo module) and Name autocomplete (TZ 3.4) arrive with their features.
@Composable
fun ItemEditorScreen(navController: NavController, listId: Long, itemId: Long) {
    val vm: ItemEditorViewModel = viewModel(factory = ItemEditorViewModel.factory(listId, itemId))
    val context = LocalContext.current
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(if (vm.isEdit) R.string.action_edit else R.string.fab_new_item),
            onBack = { navController.popBackStack() },
            actions = {
                IconButton(
                    enabled = vm.name.isNotBlank(),
                    onClick = { vm.save { navController.popBackStack() } },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            FloatingLabelTextField(
                value = vm.name,
                onValueChange = vm::onNameChange,
                label = stringResource(R.string.field_name),
                maxLength = Limits.ITEM_TEXT,
                autoFocus = true,
            )
            // Dictionary autocomplete drop-down (TZ 3.4): tap substitutes the text.
            // Same background as all menus (TZ 8).
            if (vm.suggestions.isNotEmpty()) {
                Surface(
                    color = menuContainerColor,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        vm.suggestions.forEach { suggestion ->
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.pickSuggestion(suggestion) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            FloatingLabelTextField(
                value = vm.quantity,
                onValueChange = { vm.quantity = it },
                label = stringResource(R.string.field_quantity),
                maxLength = Limits.QUANTITY,
                modifier = Modifier.padding(top = 8.dp),
            )
            FloatingLabelTextField(
                value = vm.unit,
                onValueChange = { vm.unit = it },
                label = stringResource(R.string.field_unit),
                maxLength = Limits.UNIT,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Photos (TZ 3.3): up to Limits.MAX_PHOTOS, camera or gallery (shared module, TZ 8).
            val photoFiles = vm.photos.map { PhotoManager.fileFor(context, it.fileName) }
            PhotoStrip(
                files = photoFiles,
                canAdd = vm.photos.size < Limits.MAX_PHOTOS,
                onPicked = vm::addPhoto,
                onOpen = { index -> viewerIndex = index },
                modifier = Modifier.padding(top = 16.dp),
            )
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
