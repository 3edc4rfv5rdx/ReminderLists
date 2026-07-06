package com.reminderlists.ui.screens.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.DateField
import com.reminderlists.ui.components.FloatingLabelTextField
import com.reminderlists.ui.components.PhotoStrip
import com.reminderlists.ui.components.PhotoViewerDialog
import com.reminderlists.ui.components.PriorityEditor
import com.reminderlists.ui.components.SoundField
import com.reminderlists.ui.components.TagsField
import com.reminderlists.ui.components.TimeField
import com.reminderlists.ui.components.TimePickerDialog
import com.reminderlists.ui.components.TimePresetRow
import com.reminderlists.ui.components.WeekdayPicker
import com.reminderlists.util.Limits

// Reminder add/edit form (TZ 4.2): common top fields, then type-dependent firing fields.
// Any record here is a firing reminder; no-fire records are the Notes module (TZ 4A).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorScreen(navController: NavController, reminderId: Long, folder: ReminderFolder) {
    val vm: ReminderEditorViewModel = viewModel(factory = ReminderEditorViewModel.factory(reminderId, folder))
    val context = LocalContext.current
    val allTags by vm.allTags.collectAsState()
    val presets by vm.timePresets.collectAsState()
    val defaultSound by vm.defaultSound.collectAsState()

    // Publish validation snacks to the single app-wide host (TZ 8).
    val snackController = LocalSnackController.current
    LaunchedEffect(vm.snack) {
        vm.snack?.let { snackController?.show(it); vm.snack = null }
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var dailyPickerOpen by remember { mutableStateOf(false) }

    val presetPairs = listOf(
        stringResource(R.string.preset_morning) to presets.morning,
        stringResource(R.string.preset_day) to presets.day,
        stringResource(R.string.preset_evening) to presets.evening,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(if (vm.isEdit) R.string.action_edit else R.string.editor_new_memo),
                onBack = { navController.popBackStack() },
                actions = {
                    // Validation runs inside save (TZ 4.2): errors surface via the snackbar.
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
                // 1–2: Title (required) + Content (TZ 4.2).
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
                // 3: Tags with the «#» dictionary picker (TZ 4.2 п. 3).
                TagsField(
                    value = vm.tags,
                    onValueChange = { vm.tags = it },
                    allTags = allTags,
                )
                // 4: Photos — shared photo module (TZ 4.2 п. 4 / TZ 8).
                val photoFiles = vm.photos.map { PhotoManager.fileFor(context, it.fileName) }
                PhotoStrip(
                    files = photoFiles,
                    canAdd = vm.photos.size < Limits.MAX_PHOTOS,
                    onPicked = vm::addPhoto,
                    onOpen = { index -> viewerIndex = index },
                )
                // 6: Priority «(–) ★ ★ ★ (+)» (TZ 4.2 п. 6). Date lives inside the
                // One time block below the radio group (TZ 4.2 п. 5).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.field_priority), Modifier.weight(1f))
                    PriorityEditor(priority = vm.priority, onPriorityChange = { vm.priority = it })
                }

                // 7a: Active (Full screen alert moved down next to Loop sound, TZ 4.2).
                CheckboxRow(
                    label = stringResource(R.string.field_active),
                    checked = vm.active,
                    onCheckedChange = { vm.active = it },
                )

                // 7c: repeat type radio group — one option per line so translated labels
                // of any length fit (TZ 4.2 c).
                Column(Modifier.fillMaxWidth()) {
                    RepeatTypeOption(R.string.repeat_one_time, vm.repeatType == RepeatType.ONE_TIME) {
                        vm.setType(RepeatType.ONE_TIME)
                    }
                    RepeatTypeOption(R.string.repeat_daily, vm.repeatType == RepeatType.DAILY) {
                        vm.setType(RepeatType.DAILY)
                    }
                    RepeatTypeOption(R.string.repeat_period, vm.repeatType == RepeatType.PERIOD) {
                        vm.setType(RepeatType.PERIOD)
                    }
                }

                when (vm.repeatType) {
                    // 5, d–h: Date + Time + presets, Monthly/Yearly (mutually exclusive),
                    // Auto-remove.
                    RepeatType.ONE_TIME -> {
                        DateField(
                            value = vm.date,
                            onValueChange = { vm.date = it },
                            label = stringResource(R.string.field_date),
                        )
                        TimeField(
                            value = vm.time,
                            onValueChange = { vm.time = it },
                            label = stringResource(R.string.field_time),
                        )
                        TimePresetRow(presets = presetPairs, onPick = { vm.time = it })
                        CheckboxRow(
                            label = stringResource(R.string.field_monthly_repeat),
                            checked = vm.monthlyRepeat,
                            onCheckedChange = vm::onMonthlyRepeatChange,
                        )
                        CheckboxRow(
                            label = stringResource(R.string.field_yearly_repeat),
                            checked = vm.yearlyRepeat,
                            onCheckedChange = vm::onYearlyRepeatChange,
                        )
                        CheckboxRow(
                            label = stringResource(R.string.field_auto_remove),
                            checked = vm.autoRemove,
                            onCheckedChange = { vm.autoRemove = it },
                        )
                    }

                    // d′–f′: times list as chips + weekday presets/toggles.
                    RepeatType.DAILY -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.field_times), Modifier.weight(1f))
                            IconButton(onClick = { dailyPickerOpen = true }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_time))
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            vm.dailyTimes.forEach { entry ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(entry) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_delete),
                                            modifier = Modifier.clickable { vm.removeDailyTime(entry) },
                                        )
                                    },
                                )
                            }
                        }
                        WeekdayPicker(
                            mask = vm.weekdaysMask,
                            onMaskChange = { vm.weekdaysMask = it },
                        )
                    }

                    // e″–h″: From/To (full date or bare day number), time + presets, weekdays.
                    RepeatType.PERIOD -> {
                        DateField(
                            value = vm.periodFrom,
                            onValueChange = { vm.periodFrom = it },
                            label = stringResource(R.string.field_period_from),
                        )
                        DateField(
                            value = vm.periodTo,
                            onValueChange = { vm.periodTo = it },
                            label = stringResource(R.string.field_period_to),
                        )
                        TimeField(
                            value = vm.time,
                            onValueChange = { vm.time = it },
                            label = stringResource(R.string.field_time),
                        )
                        TimePresetRow(presets = presetPairs, onPick = { vm.time = it })
                        WeekdayPicker(
                            mask = vm.weekdaysMask,
                            onMaskChange = { vm.weekdaysMask = it },
                        )
                    }
                }

                // Common firing options for every type (TZ 4.2): Full screen alert (7b, forced
                // on for Period), Loop sound (i), then the Sound field.
                CheckboxRow(
                    label = stringResource(R.string.field_full_screen_alert),
                    checked = vm.fullScreenAlert,
                    onCheckedChange = vm::setFullScreen,
                    enabled = vm.repeatType != RepeatType.PERIOD,
                )
                CheckboxRow(
                    label = stringResource(R.string.field_loop_sound),
                    checked = vm.loopSound,
                    onCheckedChange = { vm.loopSound = it },
                )
                SoundField(
                    label = stringResource(R.string.field_sound),
                    defaultLabel = stringResource(R.string.sound_default),
                    value = vm.soundUri,
                    onPick = { vm.soundUri = it },
                    defaultPreviewValue = defaultSound,
                )
            }
        }
    }

    if (dailyPickerOpen) {
        TimePickerDialog(
            initial = null,
            onPick = vm::addDailyTime,
            onDismiss = { dailyPickerOpen = false },
        )
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

// Compact 36dp touch targets: the form stacks many checkbox/radio rows (user rule).
@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            Text(label)
        }
    }
}

@Composable
private fun RepeatTypeOption(
    labelRes: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
