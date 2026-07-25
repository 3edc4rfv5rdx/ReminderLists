package com.reminderlists.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

// Interval repeat field «(–) | NNNN | (+)  [unit ▾]» (TZ 4.2 f‴): a framed segmented stepper —
// minus / typed number / plus — with a floating «Every» label cut into its top border, plus a
// separate unit dropdown. The single reusable interval control for the app (TZ 8): Timer reuses
// it with its own label and a shorter unit list (TZ 4.2 c′).
@Composable
fun IntervalField(
    count: String,
    onCountChange: (String) -> Unit,
    unit: IntervalUnit,
    onUnitChange: (IntervalUnit) -> Unit,
    modifier: Modifier = Modifier,
    labelRes: Int = R.string.field_interval_every,
    units: List<IntervalUnit> = IntervalUnit.entries,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // The number is typed directly (e.g. 58) or nudged with the buttons; min 1 (TZ 4.2 f‴).
    val parsed = count.toIntOrNull() ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Framed «(–) | N | (+)» with the «Every» label sitting on the top border.
        Box {
            Row(
                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onCountChange((parsed - 1).coerceAtLeast(1).toString()) },
                    enabled = parsed > 1,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.interval_decrease))
                }
                VerticalDivider(Modifier.height(28.dp))
                BasicTextField(
                    value = count,
                    // Digits only, capped at 4 so the field can't grow absurd; empty is allowed
                    // while typing (validation catches a blank on Save).
                    onValueChange = { new -> onCountChange(new.filter { it.isDigit() }.take(4)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.width(56.dp).padding(horizontal = 4.dp),
                )
                VerticalDivider(Modifier.height(28.dp))
                IconButton(onClick = { onCountChange((parsed + 1).coerceAtLeast(1).toString()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.interval_increase))
                }
            }
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = (-7).dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(intervalUnitNoun(unit), parsed.coerceAtLeast(1)),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                units.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(stringResource(intervalUnitLabel(entry))) },
                        onClick = { onUnitChange(entry); menuOpen = false },
                    )
                }
            }
        }
    }
}

// Bare unit noun declined by the entered count (e.g. «day/days», «дня/дней») for the picker
// button, so it composes with the floating «Every» label into «Every 5 days» (TZ 4.2 f‴).
private fun intervalUnitNoun(unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.MINUTES -> R.plurals.interval_noun_minutes
    IntervalUnit.HOURS -> R.plurals.interval_noun_hours
    IntervalUnit.DAYS -> R.plurals.interval_noun_days
    IntervalUnit.WEEKS -> R.plurals.interval_noun_weeks
    IntervalUnit.MONTHS -> R.plurals.interval_noun_months
}

// Neutral, number-independent unit name for the dropdown menu items (e.g. «Minutes», «Дни»).
private fun intervalUnitLabel(unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.MINUTES -> R.string.interval_unit_minutes
    IntervalUnit.HOURS -> R.string.interval_unit_hours
    IntervalUnit.DAYS -> R.string.interval_unit_days
    IntervalUnit.WEEKS -> R.string.interval_unit_weeks
    IntervalUnit.MONTHS -> R.string.interval_unit_months
}

// Singular "Every <unit>" phrase (count == 1, no number) — kept separate because Slavic «one»
// plural category also covers 21, 31… where the number must show, so it can't drop it (TZ 4.6 / 8).
private fun intervalEveryOne(unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.MINUTES -> R.string.interval_every_one_minutes
    IntervalUnit.HOURS -> R.string.interval_every_one_hours
    IntervalUnit.DAYS -> R.string.interval_every_one_days
    IntervalUnit.WEEKS -> R.string.interval_every_one_weeks
    IntervalUnit.MONTHS -> R.string.interval_every_one_months
}

// Plural "Every %d <units>" phrase (count >= 2) — full per-number declension via <plurals>.
private fun intervalEveryPlural(unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.MINUTES -> R.plurals.interval_every_minutes
    IntervalUnit.HOURS -> R.plurals.interval_every_hours
    IntervalUnit.DAYS -> R.plurals.interval_every_days
    IntervalUnit.WEEKS -> R.plurals.interval_every_weeks
    IntervalUnit.MONTHS -> R.plurals.interval_every_months
}

// Fully declined "Every N unit" summary, shared by the editor and reminder cards (TZ 4.6 / 8).
@Composable
fun intervalSummary(count: Int, unit: IntervalUnit): String =
    if (count <= 1) stringResource(intervalEveryOne(unit))
    else pluralStringResource(intervalEveryPlural(unit), count, count)

// Row of equal-width quick-pick buttons, one label each — the Timer duration presets
// «+5 / +15 / …» (TZ 4.2 c′). Picking one replaces the value, it does not add to it.
@Composable
fun QuickPickRow(
    labels: List<String>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            OutlinedButton(
                onClick = { onPick(index) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f),
            ) {
                // One centred line: without this the label wraps into two lines inside the
                // narrow equal-width button.
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
                // Tighter than the default button height: no vertical padding and a smaller time
                // line, so the two-line preset stays compact.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(time, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
