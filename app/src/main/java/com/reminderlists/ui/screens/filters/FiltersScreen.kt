package com.reminderlists.ui.screens.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.reminderlists.data.filter.FilterStore
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.filter.TabFilter
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.DateField
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.DialogDismissButton
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.PriorityEditor
import com.reminderlists.ui.components.TagModeToggle
import com.reminderlists.ui.components.TagsField
import com.reminderlists.util.Dates
import com.reminderlists.util.TextFormat

// Filters (TZ 4.3): Date from/to + Tags (OR/AND) + Priority + Active only, applied to the
// active tab. Mutually exclusive with the Tag Filter (4.4) — applying here clears any
// tag-cloud selection.
@Composable
fun FiltersScreen(navController: NavController, tab: FilterTab) {
    val vm: FiltersViewModel = viewModel(factory = FiltersViewModel.factory(tab))
    val allTags by vm.allTagNames.collectAsState()
    val snackController = LocalSnackController.current
    val noMatchesMsg = stringResource(R.string.filter_no_matches)

    // Draft seeded from the applied field-filter; Back discards, OK commits.
    val applied = remember { FilterStore.current(tab) }
    var fromText by remember { mutableStateOf(applied.dateFrom.orEmpty()) }
    var toText by remember { mutableStateOf(applied.dateTo.orEmpty()) }
    var tagsText by remember { mutableStateOf(applied.tagNames.joinToString(", ")) }
    var tagsMode by remember { mutableStateOf(applied.tagNamesMode) }
    var priority by remember { mutableIntStateOf(applied.priority) }
    var activeOnly by remember { mutableStateOf(applied.activeOnly) }
    var swapAsked by remember { mutableStateOf(false) }

    val fromDate = fromText.trim().ifBlank { null }?.let { Dates.parseDate(it) }
    val toDate = toText.trim().ifBlank { null }?.let { Dates.parseDate(it) }
    val fromError = fromText.isNotBlank() && fromDate == null
    val toError = toText.isNotBlank() && toDate == null
    // From after To is offered a swap on OK rather than blocking (TZ 4.3, user request).
    val rangeError = fromDate != null && toDate != null && fromDate > toDate
    // OK is only hard-blocked by an unparseable date; the range is recoverable via the swap.
    val okEnabled = !fromError && !toError

    // Notes don't fire, so the "Active only" toggle isn't part of their Filters set (TZ 4A.5).
    val showActiveOnly = tab == FilterTab.REMINDERS

    // Build and apply a draft, or warn (blue) and stay if it matches nothing.
    val applyFilter: (String?, String?) -> Unit = { from, to ->
        val draft = TabFilter(
            dateFrom = from,
            dateTo = to,
            tagNames = TextFormat.parseTags(tagsText).toSet(),
            tagNamesMode = tagsMode,
            priority = priority,
            activeOnly = activeOnly && showActiveOnly,
        )
        if (draft.isActive && vm.matchCount(draft) == 0) {
            snackController?.info(noMatchesMsg)
        } else {
            FilterStore.update(tab) { draft }
            navController.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_filters),
            onBack = { navController.popBackStack() },
            actions = {
                // x — reset every field (applied only on OK, TZ 4.3).
                IconButton(onClick = {
                    fromText = ""
                    toText = ""
                    tagsText = ""
                    priority = 0
                    activeOnly = false
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
                // v — apply; if From is after To, ask to swap first.
                IconButton(
                    enabled = okEnabled,
                    onClick = {
                        if (rangeError) {
                            swapAsked = true
                        } else {
                            applyFilter(fromText.trim().ifBlank { null }, toText.trim().ifBlank { null })
                        }
                    },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_ok))
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DateField(
                value = fromText,
                onValueChange = { fromText = it },
                label = stringResource(R.string.filter_date_from),
                modifier = Modifier.fillMaxWidth(),
                isError = fromError,
                supportingText = if (fromError) stringResource(R.string.error_date_invalid) else null,
            )
            DateField(
                value = toText,
                onValueChange = { toText = it },
                label = stringResource(R.string.filter_date_to),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                isError = toError || rangeError,
                supportingText = when {
                    toError -> stringResource(R.string.error_date_invalid)
                    rangeError -> stringResource(R.string.error_date_range)
                    else -> null
                },
            )
            TagsField(
                value = tagsText,
                onValueChange = { tagsText = it },
                allTags = allTags,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            TagModeToggle(
                mode = tagsMode,
                onModeChange = { tagsMode = it },
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.field_priority))
                PriorityEditor(priority = priority, onPriorityChange = { priority = it })
            }
            if (showActiveOnly) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.filter_active_only), Modifier.weight(1f))
                    Switch(checked = activeOnly, onCheckedChange = { activeOnly = it })
                }
            }
        }
    }

    // From after To — offer to swap the two dates, then apply (TZ 4.3).
    if (swapAsked) {
        AlertDialog(
            onDismissRequest = { swapAsked = false },
            text = { Text(stringResource(R.string.filter_swap_dates_q)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.action_ok),
                    onClick = {
                        val newFrom = toText
                        val newTo = fromText
                        fromText = newFrom
                        toText = newTo
                        swapAsked = false
                        applyFilter(newFrom.trim().ifBlank { null }, newTo.trim().ifBlank { null })
                    },
                )
            },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.action_cancel)) { swapAsked = false }
            },
        )
    }
}
