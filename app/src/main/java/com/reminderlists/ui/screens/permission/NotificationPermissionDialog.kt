package com.reminderlists.ui.screens.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.reminderlists.R

// Rationale before requesting POST_NOTIFICATIONS (TZ 4.10): without it reminders — full-screen
// included — never show, so explain why first. When the user has blocked it for good, point them
// to system settings instead of firing a request that no longer prompts.
@Composable
fun NotificationPermissionDialog(
    blocked: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_perm_title)) },
        text = {
            Text(stringResource(if (blocked) R.string.notif_perm_blocked else R.string.notif_perm_rationale))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (blocked) R.string.notif_perm_open_settings else R.string.notif_perm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notif_perm_not_now)) }
        },
    )
}
