package com.reminderlists.ui.components

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.data.sound.SoundCatalog
import com.reminderlists.data.sound.SoundStore
import kotlinx.coroutines.launch

// Self-contained sound field (TZ 4.2 j / 5, one reusable element per TZ 8). A single framed
// row (like the date/time fields): a wide choose button with a ▾ dropdown of Default, device
// ringtones and attached files — each row previewable (▷) — plus a ▷/⏹ preview of the current
// pick and a 📎 file-picker, all internal. The caller supplies the current value, where to
// store the pick, and what the Default entry resolves to for preview (defaultPreviewValue:
// the Settings default sound in the editor; null = the system alarm, as in Settings itself).
@Composable
fun SoundField(
    label: String,
    defaultLabel: String,
    value: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    defaultPreviewValue: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var systemSounds by remember { mutableStateOf<List<SoundCatalog.SystemSound>>(emptyList()) }
    var userSounds by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        systemSounds = SoundCatalog.systemAlarms(context)
        userSounds = SoundStore.listSounds(context)
    }

    // Preview owns its MediaPlayer so the field is fully self-contained; released on dispose.
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    val stopPreview = {
        player?.release()
        player = null
        playing = false
    }
    // What a choice actually plays: a concrete sound as-is; the Default entry (null) plays the
    // resolved default so the editor previews the Settings sound, not the raw system ringtone.
    fun effectiveUri(choice: String?) = when {
        choice != null -> SoundStore.mediaUri(context, choice)
        defaultPreviewValue != null -> SoundStore.mediaUri(context, defaultPreviewValue)
        else -> SoundStore.systemDefaultAlarm(context)
    }
    fun preview(choice: String?) {
        stopPreview()
        val uri = effectiveUri(choice) ?: return
        try {
            // prepareAsync keeps a large user file from blocking the UI thread (same pattern
            // as SoundService); release() in stopPreview is legal in any player state.
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener { stopPreview() }
                setOnErrorListener { _, _, _ -> stopPreview(); true }
                setOnPreparedListener { start() }
                prepareAsync()
            }
            playing = true
        } catch (_: Exception) {
            stopPreview()
        }
    }
    val togglePreview = { if (playing) stopPreview() else preview(value) }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { src ->
            scope.launch {
                val name = SoundStore.importSound(context, src)
                if (name != null) {
                    userSounds = SoundStore.listSounds(context)
                    onPick(name)
                }
            }
        }
    }

    val current = when {
        value == null -> defaultLabel
        else -> systemSounds.firstOrNull { it.uri == value }?.title
            ?: userSounds.firstOrNull { it == value }
            ?: value
    }

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Wide choose button with the current sound as its label + ▾, anchoring the menu.
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SoundMenuItem(defaultLabel, onPreview = { preview(null) }) {
                        stopPreview(); onPick(null); menuOpen = false
                    }
                    systemSounds.forEach { sound ->
                        SoundMenuItem(sound.title, onPreview = { preview(sound.uri) }) {
                            stopPreview(); onPick(sound.uri); menuOpen = false
                        }
                    }
                    userSounds.forEach { name ->
                        SoundMenuItem(name, onPreview = { preview(name) }) {
                            stopPreview(); onPick(name); menuOpen = false
                        }
                    }
                }
            }
            IconButton(onClick = togglePreview) {
                Icon(
                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.action_play_sound),
                )
            }
            IconButton(onClick = { picker.launch("audio/*") }) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.action_attach_sound))
            }
        }
        // Floating label sitting on the top border, cut into it like an OutlinedTextField.
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (-7).dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
        )
    }
}

// A menu row with the sound name and a trailing ▷ to preview it before picking (TZ 4.2 j).
@Composable
private fun SoundMenuItem(label: String, onPreview: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play_sound))
            }
        },
        onClick = onClick,
    )
}
