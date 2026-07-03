package com.reminderlists.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.reminderlists.R

// Single reusable PIN gate dialog (TZ 3.6 / 4A.4). UI gate only — DB is not encrypted.
// Scaffold: collects a PIN and reports it; verification against list/note/default PIN is
// done by the caller/repository.
@Composable
fun PinDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FloatingLabelTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit) },
                label = "PIN",
                maxLength = 8,
                keyboardType = KeyboardType.NumberPassword,
                autoFocus = true,
            )
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.action_ok), onClick = { onSubmit(pin) })
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}
