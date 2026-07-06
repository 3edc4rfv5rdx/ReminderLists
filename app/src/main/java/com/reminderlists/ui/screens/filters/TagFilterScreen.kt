package com.reminderlists.ui.screens.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.toMutableStateList
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
import com.reminderlists.data.filter.TabFilter
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.TagModeToggle

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
                // v — apply the drafted selection to this tab's filter and return. Building a
                // fresh filter clears any Filters (4.3) fields (mutually exclusive). A tag
                // filter that matches nothing warns (blue) and stays put instead of applying.
                IconButton(
                    onClick = {
                        val draft = TabFilter(tagIds = selected.toSet(), tagMode = mode)
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
            // OR/AND toggle — only affects results when 2+ tags are picked (TZ 4.4).
            TagModeToggle(mode = mode, onModeChange = { mode = it })

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
