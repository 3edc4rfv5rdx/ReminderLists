package com.reminderlists.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.data.filter.FilterIndicator

// Top App Bar filter badge (TZ 3.9): All / T / F / TF for the tab's active filter state.
// A neutral pill when All (nothing applied), accent-tinted when a filter is on.
@Composable
fun FilterIndicatorBadge(indicator: FilterIndicator, modifier: Modifier = Modifier) {
    val active = indicator != FilterIndicator.ALL
    val labelRes = when (indicator) {
        FilterIndicator.ALL -> R.string.filter_ind_all
        FilterIndicator.T -> R.string.filter_ind_tag
        FilterIndicator.F -> R.string.filter_ind_filters
        FilterIndicator.TF -> R.string.filter_ind_both
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
