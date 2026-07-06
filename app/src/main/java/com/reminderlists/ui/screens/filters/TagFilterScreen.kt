package com.reminderlists.ui.screens.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.dao.TagUsage
import com.reminderlists.data.filter.FilterStore
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.filter.TagMode
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.LocalSnackController

// Five font-size tiers for the tag cloud (TZ 4.4): rarer tags smaller, most-used largest.
private val TIER_SIZES = listOf(14.sp, 16.sp, 19.sp, 22.sp, 26.sp)

// Tag Filter (TZ 4.4): tag cloud sized by usage, OR/AND toggle, combinable with Filters.
// Selection is drafted here and applied to the tab's filter only on OK (v).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterScreen(navController: NavController, tab: FilterTab) {
    val vm: TagFilterViewModel = viewModel(factory = TagFilterViewModel.factory(tab))
    val tags by vm.tags.collectAsState()
    val snackController = LocalSnackController.current
    val noMatchesMsg = stringResource(R.string.filter_no_matches)

    // Draft seeded from the currently applied filter; Back discards, OK commits.
    val applied = remember { FilterStore.current(tab) }
    val selected = remember { applied.tagIds.toMutableStateList() }
    var mode by remember { mutableStateOf(applied.tagMode) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_tag_filter),
            onBack = { navController.popBackStack() },
            actions = {
                // x — clear the draft selection (does not apply until OK, matching Filters, TZ 4.3).
                IconButton(onClick = { selected.clear() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
                // v — apply the drafted selection to this tab's filter and return. A tag
                // filter that matches nothing warns (blue) and stays put instead of applying.
                IconButton(
                    onClick = {
                        val draft = applied.copy(tagIds = selected.toSet(), tagMode = mode)
                        if (draft.tagActive && vm.matchCount(draft) == 0) {
                            snackController?.info(noMatchesMsg)
                        } else {
                            FilterStore.update(tab) { draft }
                            navController.popBackStack()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_ok))
                }
            },
        )

        if (tags.isEmpty()) {
            EmptyState(icon = Icons.Filled.Tag, text = stringResource(R.string.tags_empty))
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // OR/AND toggle — centered; only affects results when 2+ tags are picked (TZ 4.4).
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeLabel(
                        stringResource(R.string.tag_filter_mode_or),
                        active = mode == TagMode.OR,
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                    Switch(
                        checked = mode == TagMode.AND,
                        onCheckedChange = { mode = if (it) TagMode.AND else TagMode.OR },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        // Colored in both states so the toggle reads as an active accent
                        // control, not a disabled OR-side.
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onTertiary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.tertiary,
                            uncheckedBorderColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    )
                    ModeLabel(
                        stringResource(R.string.tag_filter_mode_and),
                        active = mode == TagMode.AND,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
                // Plain-language explanation of the active mode.
                Text(
                    text = stringResource(
                        if (mode == TagMode.OR) R.string.tag_filter_hint_or else R.string.tag_filter_hint_and,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            val maxCount = tags.first().count
            val minCount = tags.last().count

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    TagCloudChip(
                        tag = tag,
                        selected = tag.id in selected,
                        fontSize = tierSize(tag.count, minCount, maxCount),
                        onToggle = {
                            if (tag.id in selected) selected.remove(tag.id) else selected.add(tag.id)
                        },
                    )
                }
            }
        }
    }
}

// Map a usage count onto one of five tiers between the least- and most-used tag (TZ 4.4).
private fun tierSize(count: Int, min: Int, max: Int) =
    if (max <= min) {
        TIER_SIZES[TIER_SIZES.size / 2]
    } else {
        val tier = ((count - min).toFloat() / (max - min) * (TIER_SIZES.size - 1)).toInt()
        TIER_SIZES[tier.coerceIn(0, TIER_SIZES.size - 1)]
    }

// OR / AND label beside the switch — the active side is accented (color + bold) so the
// current mode is obvious at a glance.
@Composable
private fun ModeLabel(text: String, active: Boolean, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TagCloudChip(
    tag: TagUsage,
    selected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onToggle: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Text(
            // "name (N)" — usage count after the tag name (TZ 4.4).
            text = "${tag.name} (${tag.count})",
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
