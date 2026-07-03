package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Folder/list comment shown at the bottom of the content, under a divider (TZ 3.1 / 3.2).
@Composable
fun CommentFooter(comment: String?) {
    if (comment == null) return
    Column {
        HorizontalDivider(
            thickness = 3.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = comment,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
