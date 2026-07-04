package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

// Record-row title: name + optional "(counts)" — one size, one color, shared
// weight/strikethrough (TZ 8). Used by folder/list/reminder rows alike.
@Composable
fun RowTitle(
    name: String,
    countsText: String?,
    fontWeight: FontWeight?,
    textDecoration: TextDecoration? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = fontWeight,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (countsText != null) {
            Text(
                text = " $countsText",
                maxLines = 1,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
            )
        }
    }
}
