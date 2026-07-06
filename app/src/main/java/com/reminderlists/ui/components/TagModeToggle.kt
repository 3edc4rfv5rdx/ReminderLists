package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.data.filter.TagMode

// Shared OR/AND tag-combination toggle (TZ 4.3 / 4.4): centered OR–switch–AND with the active
// side accented and a plain-language hint on the same line. Used by both the Tag Filter cloud
// and the Filters Tags field so there is one control, not two.
@Composable
fun TagModeToggle(mode: TagMode, onModeChange: (TagMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeLabel(
            stringResource(R.string.tag_filter_mode_or),
            active = mode == TagMode.OR,
            accent = MaterialTheme.colorScheme.tertiary,
        )
        Switch(
            checked = mode == TagMode.AND,
            onCheckedChange = { onModeChange(if (it) TagMode.AND else TagMode.OR) },
            modifier = Modifier.padding(horizontal = 8.dp),
            // Colored in both states so the toggle reads as an active accent control.
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
        // Plain-language explanation of the active mode, on the same line.
        Text(
            text = stringResource(
                if (mode == TagMode.OR) R.string.tag_filter_hint_or else R.string.tag_filter_hint_and,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

// OR / AND label beside the switch — the active side is accented (color + bold).
@Composable
private fun ModeLabel(text: String, active: Boolean, accent: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
