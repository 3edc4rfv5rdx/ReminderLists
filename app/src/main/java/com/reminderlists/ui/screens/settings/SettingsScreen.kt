package com.reminderlists.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.PinSetupDialog
import com.reminderlists.ui.components.SoundField
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.ui.screens.about.AboutDialog
import com.reminderlists.util.Limits

// Settings (TZ 5): theme, language, dictionary, enable reminders, welcome, keep-screen-on,
// logs, time presets, default sound, default PIN, backup/restore. Wired: enable reminders,
// default sound Duration/level, default PIN, dictionary; the rest are placeholders.
@Composable
fun SettingsScreen(navController: NavController, contentPadding: PaddingValues) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    var pinDialogOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }

    val enableReminders by vm.enableReminders.collectAsState()
    val soundDuration by vm.soundDuration.collectAsState()
    val soundLevel by vm.soundLevel.collectAsState()
    val defaultSoundUri by vm.defaultSoundUri.collectAsState()

    // Settings is now a bottom-bar tab (TZ 3.9): no back arrow, content inset from the bar.
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        AppTopBar(
            title = stringResource(R.string.menu_settings),
            actions = {
                // About moved here from the Lists/Reminders menus (TZ 4.9).
                IconButton(onClick = { aboutOpen = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.menu_about))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // TODO wire theme/language (TZ 5).
            SettingRow(stringResource(R.string.settings_theme))
            SettingRow(stringResource(R.string.settings_language))

            // Dictionary management screen entry (TZ 3.4).
            SettingRow(
                stringResource(R.string.menu_dictionary),
                arrow = true,
                onClick = { navController.navigate(Routes.DICTIONARY) { launchSingleTop = true } },
            )

            // Silence mode (TZ 5 / 4.10): cancels/re-arms all alarms.
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_enable_reminders), Modifier.weight(1f))
                Switch(checked = enableReminders, onCheckedChange = { vm.setEnableReminders(it) })
            }

            // TODO wire keep-screen-on / logs / time presets (TZ 5).
            SettingRow(stringResource(R.string.settings_keep_screen_on))
            SettingRow(stringResource(R.string.settings_write_logs))
            SettingRow(stringResource(R.string.settings_time_presets))

            // Default sound: one self-contained field (picker + preview + Add file), then
            // Duration and Sound level (TZ 5 / 4.10).
            SoundField(
                label = stringResource(R.string.settings_default_sound),
                defaultLabel = stringResource(R.string.settings_sound_system_default),
                value = defaultSoundUri,
                onPick = { vm.setDefaultSound(it) },
            )
            Text("${stringResource(R.string.settings_sound_duration)}: ${soundDuration}s")
            Slider(
                value = soundDuration.toFloat(),
                onValueChange = { vm.setSoundDuration(it.toInt()) },
                valueRange = 0f..Limits.MAX_SOUND_DURATION.toFloat(),
            )
            Text("${stringResource(R.string.settings_sound_level)}: $soundLevel")
            Slider(
                value = soundLevel.toFloat(),
                onValueChange = { vm.setSoundLevel(it.toInt()) },
                valueRange = 0f..Limits.MAX_SOUND_LEVEL.toFloat(),
            )

            SettingRow(
                stringResource(R.string.settings_default_pin),
                arrow = true,
                onClick = { pinDialogOpen = true },
            )
            SettingRow(stringResource(R.string.menu_backup_restore), arrow = true)
        }
    }

    // Default PIN editor (TZ 5): masked, typed twice, never shown back; saving an
    // empty value clears the PIN.
    if (pinDialogOpen) {
        PinSetupDialog(
            title = stringResource(R.string.settings_default_pin),
            emptyPinAllowed = true,
            onSave = { pin ->
                vm.setDefaultPin(pin)
                pinDialogOpen = false
            },
            onDismiss = { pinDialogOpen = false },
        )
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

// A settings line; screen-exit rows (dictionary, PIN, backup) get a trailing chevron.
@Composable
private fun SettingRow(text: String, arrow: Boolean = false, onClick: (() -> Unit)? = null) {
    val modifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 6.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        if (arrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
