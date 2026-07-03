package com.reminderlists.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.reminderlists.R
import com.reminderlists.data.photo.PhotoManager
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Shared photo UI (TZ 8): add button with camera/gallery menu, thumbnail strip for editors,
// fullscreen viewer. No runtime permissions — Photo Picker + camera via FileProvider.

// "Add photo" icon button: opens a camera/gallery menu, imports the picked image.
@Composable
fun AddPhotoButton(
    enabled: Boolean,
    onPicked: (Uri) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    // Survives the activity restart while the camera app is in front.
    var cameraTempPath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraTempPath
        cameraTempPath = null
        if (success && path != null) onPicked(PhotoManager.uriFor(context, File(path)))
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }

    Box {
        IconButton(enabled = enabled, onClick = { menuOpen = true }) {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = stringResource(R.string.action_add_photo),
                tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
            )
        }
        AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.photo_source_camera)) },
                onClick = {
                    menuOpen = false
                    val target = PhotoManager.newCameraCaptureFile(context)
                    cameraTempPath = target.absolutePath
                    cameraLauncher.launch(PhotoManager.uriFor(context, target))
                },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                text = { Text(stringResource(R.string.photo_source_gallery)) },
                onClick = {
                    menuOpen = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }
    }
}

// Thumbnail strip for editors (TZ 3.3): photos + add button, limit handled by the caller.
@Composable
fun PhotoStrip(
    files: List<File>,
    canAdd: Boolean,
    onPicked: (Uri) -> Unit,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        AddPhotoButton(enabled = canAdd, onPicked = onPicked)
        LazyRow(Modifier.weight(1f)) {
            itemsIndexed(files, key = { _, file -> file.name }) { index, file ->
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen(index) },
                )
            }
        }
    }
}

// Fullscreen photo viewer (TZ 3.3 / 8): swipe between photos, add more, delete with confirmation.
@Composable
fun PhotoViewerDialog(
    files: List<File>,
    canAdd: Boolean,
    onPicked: (Uri) -> Unit,
    onDelete: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    initialPage: Int = 0,
) {
    // Close when the last photo disappears (deleted here or elsewhere).
    LaunchedEffect(files.isEmpty()) {
        if (files.isEmpty()) onDismiss()
    }
    if (files.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, files.size - 1),
    ) { files.size }
    var confirmDelete by remember { mutableStateOf(false) }
    // Scope lives with the viewer, not with the confirm dialog — the dialog closes before
    // the export+delete sequence runs, and its own scope would cancel mid-way.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = files[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${files.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    AddPhotoButton(enabled = canAdd, onPicked = onPicked, tint = Color.White)
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeletePhotoDialog(
            onConfirm = { saveToGallery ->
                confirmDelete = false
                val page = pagerState.currentPage
                scope.launch {
                    withContext(NonCancellable) {
                        if (saveToGallery) PhotoManager.exportToGallery(context, files[page].name)
                        onDelete(page)
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// Photo delete confirmation with the "save to gallery" checkbox (TZ 8, user rule).
@Composable
private fun DeletePhotoDialog(
    onConfirm: (saveToGallery: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var saveToGallery by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_photo_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveToGallery = !saveToGallery },
            ) {
                Checkbox(checked = saveToGallery, onCheckedChange = { saveToGallery = it })
                Text(stringResource(R.string.save_to_gallery))
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_delete),
                onClick = { onConfirm(saveToGallery) },
                destructive = true,
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}
