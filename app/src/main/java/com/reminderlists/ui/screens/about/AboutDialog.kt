package com.reminderlists.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.BuildConfig
import com.reminderlists.R

// About dialog (TZ 4.9). Version/build come from Gradle (BuildConfig), not the DB.
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_description))
                Text(
                    "${stringResource(R.string.about_version)}: ${BuildConfig.VERSION_NAME}",
                    Modifier.padding(top = 16.dp),
                )
                Text("${stringResource(R.string.about_build)}: ${BuildConfig.VERSION_CODE}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
