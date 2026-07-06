package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.util.Weekdays

private val dayLabels = listOf(
    R.string.day_mo,
    R.string.day_tu,
    R.string.day_we,
    R.string.day_th,
    R.string.day_fr,
    R.string.day_sa,
    R.string.day_su,
)

// Weekday selection for Daily / Period (TZ 4.2 e′–f′ / h″): Every day / Weekdays preset
// buttons + seven Mo..Su toggles, selected days highlighted with the primary color.
// Bitmask bit0 = Mon (TZ 6.2).
@Composable
fun WeekdayPicker(mask: Int, onMaskChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Presets toggle their own range on/off (TZ 4.2 e′).
            OutlinedButton(
                onClick = { onMaskChange(Weekdays.toggleRange(mask, Weekdays.ALL)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).height(34.dp),
            ) {
                Text(stringResource(R.string.weekdays_every_day))
            }
            OutlinedButton(
                onClick = { onMaskChange(Weekdays.toggleRange(mask, Weekdays.WEEKDAYS)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).height(34.dp),
            ) {
                Text(stringResource(R.string.weekdays_weekdays))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            dayLabels.forEachIndexed { index, labelRes ->
                val selected = Weekdays.has(mask, index)
                Surface(
                    onClick = { onMaskChange(Weekdays.toggle(mask, index)) },
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
