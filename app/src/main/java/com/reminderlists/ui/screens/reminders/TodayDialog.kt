package com.reminderlists.ui.screens.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reminderlists.R
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.PriorityStars
import com.reminderlists.util.Dates
import java.time.LocalTime

// Today dialog (TZ 4.11): an informational list of every active reminder firing today,
// sorted by time, with a divider between past and upcoming times. Tapping a row opens the
// editor. When reminders are off it still shows, with a silent-mode note on top.
@Composable
fun TodayDialog(
    items: List<TodayItem>,
    nowTime: LocalTime,
    remindersEnabled: Boolean,
    onItemClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogConfirmButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
        title = { Text(stringResource(R.string.action_today)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!remindersEnabled) {
                    Text(
                        stringResource(R.string.today_reminders_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (items.isEmpty()) {
                    Text(stringResource(R.string.empty_today))
                } else {
                    items.forEachIndexed { index, item ->
                        val prev = items.getOrNull(index - 1)
                        // Thin divider between the last past time and the first upcoming one (TZ 4.11).
                        if (prev != null && !prev.time.isAfter(nowTime) && item.time.isAfter(nowTime)) {
                            HorizontalDivider(
                                thickness = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        TodayRow(item, past = item.time.isBefore(nowTime), onItemClick)
                    }
                }
            }
        },
    )
}

@Composable
private fun TodayRow(item: TodayItem, past: Boolean, onClick: (Long) -> Unit) {
    val reminder = item.detail.reminder
    // Inactive (fired or switched off) rows are struck through whole; an active row strikes
    // only its already-passed time (TZ 4.11).
    val inactive = !reminder.active
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(reminder.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Dates.format(item.time),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textDecoration = if (past || inactive) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (inactive) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PriorityStars(reminder.priority, Modifier.padding(start = 8.dp))
            }
            if (item.detail.tags.isNotEmpty()) {
                Text(
                    item.detail.tags.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Icon(
            folderIcon(item.folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun folderIcon(folder: ReminderFolder): ImageVector = when (folder) {
    ReminderFolder.ONCE -> Icons.Filled.Event
    ReminderFolder.DAILY -> Icons.Filled.Repeat
    ReminderFolder.PERIODS -> Icons.Filled.DateRange
    ReminderFolder.MONTHLY -> Icons.Filled.CalendarMonth
    ReminderFolder.YEARLY -> Icons.Filled.CalendarMonth
}
