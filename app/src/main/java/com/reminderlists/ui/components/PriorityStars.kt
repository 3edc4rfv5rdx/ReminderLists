package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.util.Limits

// Shared priority widget (TZ 4.2 п. 6, reused by Notes): 0..Limits.PRIORITY_MAX,
// 0 = no priority.

// Card display: filled stars only; renders nothing at 0 (TZ 4.6).
@Composable
fun PriorityStars(priority: Int, modifier: Modifier = Modifier) {
    if (priority <= 0) return
    Row(modifier) {
        repeat(priority.coerceAtMost(Limits.PRIORITY_MAX)) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Editor widget «(–) ★ ★ ★ (+)»: minus/plus buttons around the star row.
@Composable
fun PriorityEditor(priority: Int, onPriorityChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(
            onClick = { onPriorityChange(priority - 1) },
            enabled = priority > 0,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.priority_decrease))
        }
        repeat(Limits.PRIORITY_MAX) { index ->
            Icon(
                imageVector = if (index < priority) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (index < priority) {
                    MaterialTheme.colorScheme.primary
                } else {
                    // Empty stars stay pale (outlineVariant) so they don't read as filled
                    // once outline is pure black on the light scheme.
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
        }
        IconButton(
            onClick = { onPriorityChange(priority + 1) },
            enabled = priority < Limits.PRIORITY_MAX,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.priority_increase))
        }
    }
}
