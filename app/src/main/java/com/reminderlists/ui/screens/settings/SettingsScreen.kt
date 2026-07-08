package com.reminderlists.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.backup.BackupManager
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.PinDialog
import com.reminderlists.ui.components.PinSetupDialog
import com.reminderlists.ui.components.SoundField
import com.reminderlists.ui.components.TimePickerDialog
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.ui.screens.about.AboutDialog
import com.reminderlists.ui.theme.AppTheme
import com.reminderlists.ui.theme.ThemeMode
import com.reminderlists.util.Dates
import com.reminderlists.util.Limits
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.launch

// Settings (TZ 5): theme (color + Light/Dark/System), language, dictionary, enable reminders,
// keep-screen-on, logs, time presets, default sound, default PIN, backup/restore.
@Composable
fun SettingsScreen(navController: NavController, contentPadding: PaddingValues) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    // Two-step for the default PIN: verify the current one (if any) before the setup editor.
    var pinGateOpen by remember { mutableStateOf(false) }
    var pinDialogOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnackController.current

    // Backup writes to Documents/ReminderLists/ by default (TZ 3.8); restore picks a file via the
    // system document picker — no storage permission, no cloud (TZ 9). Restore confirms first,
    // then swaps data and restarts the app.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) restoreUri = uri }

    val themeColor by vm.themeColor.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val fontScale by vm.fontScale.collectAsState()
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val writeLogs by vm.writeLogs.collectAsState()
    val enableReminders by vm.enableReminders.collectAsState()
    val morning by vm.morningPreset.collectAsState()
    val day by vm.dayPreset.collectAsState()
    val evening by vm.eveningPreset.collectAsState()
    val soundDuration by vm.soundDuration.collectAsState()
    val soundLevel by vm.soundLevel.collectAsState()
    val defaultSoundUri by vm.defaultSoundUri.collectAsState()
    val defaultPin by vm.defaultPin.collectAsState()

    // Settings is a bottom-bar tab (TZ 3.9): no back arrow, content inset from the bar.
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
            // Theme: Light/Dark/System mode + color preset (TZ 5).
            SectionHeader(stringResource(R.string.settings_theme))
            ThemeModeSelector(mode = themeMode, onSelect = { vm.setThemeMode(it) })
            ThemeColorSelector(selected = themeColor, onSelect = { vm.setThemeColor(it) })

            // App-wide font scale (TZ 5). Rebuilds Typography for the whole app, but only
            // once on release: drag updates a local value, persist on onValueChangeFinished.
            var sliderScale by remember(fontScale) { mutableStateOf(fontScale) }
            Text(
                "${stringResource(R.string.settings_font_size)}: ${(sliderScale * 100).toInt()}%",
                Modifier.padding(top = 8.dp),
            )
            Slider(
                value = sliderScale,
                onValueChange = { sliderScale = it },
                onValueChangeFinished = { vm.setFontScale(sliderScale) },
                valueRange = Limits.FONT_SCALE_MIN..Limits.FONT_SCALE_MAX,
                steps = 19, // snap in 5% increments (80%..180%)
            )

            // Per-app language (TZ 5). Applied via LocaleManager; opens a chooser.
            ValueRow(
                label = stringResource(R.string.settings_language),
                value = languageLabel(vm.currentLanguageTag()),
                onClick = { languageDialogOpen = true },
            )

            SectionDivider()

            // Dictionary management screen entry (TZ 3.4).
            NavRow(
                stringResource(R.string.menu_dictionary),
                onClick = { navController.navigate(Routes.DICTIONARY) { launchSingleTop = true } },
            )

            // Silence mode (TZ 5 / 4.10): cancels/re-arms all alarms.
            SwitchRow(
                stringResource(R.string.settings_enable_reminders),
                checked = enableReminders,
                onCheckedChange = { vm.setEnableReminders(it) },
            )
            SwitchRow(
                stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onCheckedChange = { vm.setKeepScreenOn(it) },
            )
            SwitchRow(
                stringResource(R.string.settings_write_logs),
                checked = writeLogs,
                onCheckedChange = { vm.setWriteLogs(it) },
            )

            SectionDivider()

            // Time presets used by the reminder form (TZ 5 / 4.2).
            SectionHeader(stringResource(R.string.settings_time_presets))
            TimePresetRow(stringResource(R.string.preset_morning), morning) { vm.setTimePreset(SettingsKeys.TIME_PRESET_MORNING, it) }
            TimePresetRow(stringResource(R.string.preset_day), day) { vm.setTimePreset(SettingsKeys.TIME_PRESET_DAY, it) }
            TimePresetRow(stringResource(R.string.preset_evening), evening) { vm.setTimePreset(SettingsKeys.TIME_PRESET_EVENING, it) }

            SectionDivider()

            // Default sound: one self-contained field (picker + preview + Add file), then
            // Duration and Sound level (TZ 5 / 4.10). SoundField carries its own label.
            SoundField(
                label = stringResource(R.string.settings_default_sound),
                defaultLabel = stringResource(R.string.settings_sound_system_default),
                value = defaultSoundUri,
                onPick = { vm.setDefaultSound(it) },
            )
            Text(
                "${stringResource(R.string.settings_sound_duration)}: ${soundDuration}s",
                Modifier.padding(top = 8.dp),
            )
            Slider(
                value = soundDuration.toFloat(),
                onValueChange = { vm.setSoundDuration(it.toInt()) },
                valueRange = 0f..Limits.MAX_SOUND_DURATION.toFloat(),
                steps = 17, // snap every 5 s (0..90)
            )
            Text("${stringResource(R.string.settings_sound_level)}: $soundLevel")
            Slider(
                value = soundLevel.toFloat(),
                onValueChange = { vm.setSoundLevel(it.toInt()) },
                valueRange = 0f..Limits.MAX_SOUND_LEVEL.toFloat(),
                steps = 19, // snap every 5 (0..100)
            )

            SectionDivider()

            NavRow(
                stringResource(R.string.settings_default_pin),
                // Gate the change behind the current PIN; skip straight to setup if none set.
                onClick = { if (defaultPin.isNullOrEmpty()) pinDialogOpen = true else pinGateOpen = true },
            )

            SectionDivider()

            // Backup/Restore (TZ 3.8).
            SectionHeader(stringResource(R.string.menu_backup_restore))
            NavRow(
                stringResource(R.string.settings_backup_create),
                onClick = {
                    scope.launch {
                        BackupManager.backupToDocuments(context)
                            .onSuccess { snack?.success(context.getString(R.string.backup_success)) }
                            .onFailure {
                                // Surface the real reason so a silent MediaStore failure is diagnosable.
                                Logger.e("Backup failed", it)
                                snack?.error("${context.getString(R.string.backup_failed)}: ${it.message}")
                            }
                    }
                },
            )
            NavRow(
                stringResource(R.string.settings_backup_restore),
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            )
        }
    }

    // Restore overwrites everything, so confirm first (TZ 3.8). On success the app restarts to
    // reopen the swapped database cleanly.
    restoreUri?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        if (BackupManager.restore(context, uri).isSuccess) restartApp(context)
                        else snack?.error(context.getString(R.string.restore_failed))
                    }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restoreUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (languageDialogOpen) {
        LanguageDialog(
            current = vm.currentLanguageTag(),
            onSelect = {
                languageDialogOpen = false
                vm.setLanguage(it) // recreates the activity with the new locale
            },
            onDismiss = { languageDialogOpen = false },
        )
    }

    // Verify the current default PIN before allowing a change/clear (TZ 3.6 / 5).
    if (pinGateOpen) {
        PinDialog(
            title = stringResource(R.string.settings_default_pin),
            verify = { it == defaultPin },
            onSuccess = {
                pinGateOpen = false
                pinDialogOpen = true
            },
            onDismiss = { pinGateOpen = false },
        )
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

// Section heading with generous top space so groups read as blocks (TZ 8: not cramped).
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.LIGHT to R.string.settings_theme_mode_light,
        ThemeMode.DARK to R.string.settings_theme_mode_dark,
        ThemeMode.SYSTEM to R.string.settings_theme_mode_system,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val modes = ThemeMode.entries
        modes.forEachIndexed { index, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(stringResource(labels.getValue(m)))
            }
        }
    }
}

@Composable
private fun ThemeColorSelector(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    // One row, scrolled horizontally — too many presets for a fixed row, but a swipeable
    // strip keeps all the colour swatches visible without wrapping.
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(theme.seed)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
                Text(stringResource(themeName(theme)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// One time-preset line: label on the left, the current time as a tappable value that opens
// the shared time picker (TZ 5 / 4.2).
@Composable
private fun TimePresetRow(label: String, time: String, onPick: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { pickerOpen = true }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(time, color = MaterialTheme.colorScheme.primary)
    }
    if (pickerOpen) {
        TimePickerDialog(
            initial = Dates.parseTime(time),
            onPick = { onPick(Dates.format(it)) },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun LanguageDialog(current: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    // null = follow the system language (TZ 5).
    val options: List<Pair<String?, Int>> = listOf(
        null to R.string.language_system,
        "en" to R.string.language_english,
        "ru" to R.string.language_russian,
        "uk" to R.string.language_ukrainian,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                options.forEach { (tag, labelRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = current == tag, onClick = { onSelect(tag) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == tag, onClick = { onSelect(tag) })
                        Text(stringResource(labelRes), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun languageLabel(tag: String?): String = stringResource(
    when (tag) {
        "en" -> R.string.language_english
        "ru" -> R.string.language_russian
        "uk" -> R.string.language_ukrainian
        else -> R.string.language_system
    },
)

// Relaunch the app after a restore so every ViewModel/DAO rebinds to the swapped database.
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

private fun themeName(theme: AppTheme): Int = when (theme) {
    AppTheme.TEAL -> R.string.theme_teal
    AppTheme.INDIGO -> R.string.theme_indigo
    AppTheme.FOREST -> R.string.theme_forest
    AppTheme.PLUM -> R.string.theme_plum
    AppTheme.AMBER -> R.string.theme_amber
    AppTheme.ORANGE -> R.string.theme_orange
    AppTheme.BEIGE -> R.string.theme_beige
    AppTheme.OLIVE -> R.string.theme_olive
}

// A switch line with roomy vertical padding (TZ 8: rows must not feel cramped on a phone).
@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// A line that navigates to another screen/dialog; trailing chevron.
@Composable
private fun NavRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// A line showing a current value on the right plus a chevron.
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
