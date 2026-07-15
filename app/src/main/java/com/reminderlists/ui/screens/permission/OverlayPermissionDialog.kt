package com.reminderlists.ui.screens.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.reminderlists.R
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.DialogDismissButton

// First-launch rationale for the "Display over other apps" grant (TZ 4.5): without it the
// full-screen alert cannot take over the screen while another app is in use — the system
// blocks activity starts from the alarm receiver and only shows a heads-up. Shown once;
// the grant itself is a system toggle, so Confirm routes to system settings.
@Composable
fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overlay_perm_title)) },
        text = { Text(stringResource(R.string.overlay_perm_rationale)) },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.notif_perm_open_settings),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.notif_perm_not_now), onDismiss)
        },
    )
}
