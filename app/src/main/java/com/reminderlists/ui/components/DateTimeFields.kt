package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.data.reminders.IntervalUnit
import com.reminderlists.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// Shared date/time form fields (TZ 4.2 / 4.3, reused by Filters and Notes): free text
// input + picker button + «x» clear. Validation stays with the caller — these components
// only surface isError/supportingText.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    FloatingLabelTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
        trailingIcon = {
            Row {
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.action_pick_date))
                }
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
    if (pickerOpen) {
        // The state lives inside this if-block, so every open re-seeds from the field text;
        // an empty field opens with today preselected.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (Dates.parseDate(value) ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.action_ok),
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            // DatePicker reports UTC midnight of the picked calendar day.
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onValueChange(Dates.format(date))
                        }
                        pickerOpen = false
                    },
                )
            },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.action_cancel)) { pickerOpen = false }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    FloatingLabelTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
        trailingIcon = {
            Row {
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.action_pick_time))
                }
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
    if (pickerOpen) {
        TimePickerDialog(
            initial = Dates.parseTime(value),
            onPick = { onValueChange(Dates.format(it)) },
            onDismiss = { pickerOpen = false },
        )
    }
}

// One time-picker dialog for the whole app (also used by the Daily times list, TZ 4.2 d′).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime?,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val seed = initial ?: LocalTime.now()
    val state = rememberTimePickerState(
        initialHour = seed.hour,
        initialMinute = seed.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_ok),
                onClick = {
                    onPick(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// Interval repeat field «Every [N] [unit ▾]» (TZ 4.2 f‴): a number input plus a unit
// dropdown. The single reusable interval control for the app (TZ 8).
@Composable
fun IntervalField(
    count: String,
    onCountChange: (String) -> Unit,
    unit: IntervalUnit,
    onUnitChange: (IntervalUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        FloatingLabelTextField(
            value = count,
            // Digits only: strip anything else so the count stays parseable (TZ 4.2 f‴).
            onValueChange = { new -> onCountChange(new.filter { it.isDigit() }) },
            label = stringResource(R.string.field_interval_every),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(0.4f),
        )
        Box(Modifier.weight(0.6f)) {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(intervalUnitLabel(unit)), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                IntervalUnit.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(stringResource(intervalUnitLabel(entry))) },
                        onClick = { onUnitChange(entry); menuOpen = false },
                    )
                }
            }
        }
    }
}

private fun intervalUnitLabel(unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.MINUTES -> R.string.interval_unit_minutes
    IntervalUnit.HOURS -> R.string.interval_unit_hours
    IntervalUnit.DAYS -> R.string.interval_unit_days
    IntervalUnit.WEEKS -> R.string.interval_unit_weeks
    IntervalUnit.MONTHS -> R.string.interval_unit_months
}

// Quick presets Morning / Day / Evening (TZ 4.2 п. e): the preset time is shown inside
// the button as a second line; values come from Settings (TZ 5 → Time presets).
@Composable
fun TimePresetRow(
    presets: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        presets.forEach { (label, time) ->
            OutlinedButton(
                onClick = { onPick(time) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label)
                    Text(time)
                }
            }
        }
    }
}
