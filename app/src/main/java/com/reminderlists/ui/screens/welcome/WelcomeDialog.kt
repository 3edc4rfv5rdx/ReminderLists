package com.reminderlists.ui.screens.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R

// First-run welcome (TZ 4.8) — implemented as a dialog (per decision), not a separate route.
// Language + theme choice; re-openable from Settings. Applied and persisted immediately.
@Composable
fun WelcomeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_description))
                // TODO language selector + theme selector (TZ 4.8 / 5).
                Text(stringResource(R.string.settings_language), Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.settings_theme), Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
    )
}
