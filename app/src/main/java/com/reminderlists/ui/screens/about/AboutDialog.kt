package com.reminderlists.ui.screens.about

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.BuildConfig
import com.reminderlists.R
import com.reminderlists.ui.components.DialogButtonRow
import com.reminderlists.ui.components.DialogConfirmButton
import com.reminderlists.ui.components.DialogDismissButton
import com.reminderlists.util.UPDATER_CONFIG
import dev.updater.Updater

// About dialog (TZ 4.9). Version and build date come from Gradle (BuildConfig),
// not the DB. The build number is the version's last component, so what is
// worth a line of its own is the day it was built.
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    // The updater draws its own dialogs, so it needs the activity rather than a
    // context — and this one has to close first, or they would sit on top of
    // each other.
    val activity = LocalActivity.current
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
                Text("${stringResource(R.string.about_build_date)}: ${BuildConfig.BUILD_DATE}")
            }
        },
        // Both buttons in the confirm slot: Update is pushed to the far left,
        // away from Ok, and Material would otherwise cluster the two together
        // on the right.
        confirmButton = {
            DialogButtonRow(
                start = {
                    // The start-up check keeps six hours between two looks at
                    // the server, which is right for a phone and useless for a
                    // build published a minute ago. This one ignores the
                    // interval and answers either way.
                    if (activity != null) {
                        DialogDismissButton(stringResource(R.string.about_update)) {
                            onDismiss()
                            Updater.checkNow(activity, UPDATER_CONFIG)
                        }
                    } else {
                        Spacer(Modifier)
                    }
                },
                end = { DialogConfirmButton(stringResource(R.string.action_ok), onDismiss) },
            )
        },
    )
}
