package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Column
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
import com.reminderlists.util.Limits

// Masked digit field — every PIN entry in the app goes through this (TZ 3.6 / 8).
@Composable
fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supportingText: String? = null,
    autoFocus: Boolean = false,
) {
    FloatingLabelTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = label,
        maxLength = Limits.PIN,
        keyboardType = KeyboardType.NumberPassword,
        password = true,
        isError = isError,
        supportingText = supportingText,
        autoFocus = autoFocus,
    )
}

// One dialog for setting a PIN (TZ 3.6 / 5): masked PIN + repeat with mismatch check.
// Serves both the list Protect dialog (empty = use the default PIN, blocked via
// emptyPinAllowed until one is set) and the Default PIN editor (empty clears the PIN).
@Composable
fun PinSetupDialog(
    title: String,
    emptyPinAllowed: Boolean,
    emptyPinHint: String? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    val valid = if (pin.isEmpty()) emptyPinAllowed else pin == repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                PinTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = stringResource(R.string.pin_label),
                    isError = pin.isEmpty() && !emptyPinAllowed,
                    supportingText = if (pin.isEmpty()) emptyPinHint else null,
                    autoFocus = true,
                )
                val mismatch = repeat.isNotEmpty() && repeat != pin
                PinTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = stringResource(R.string.pin_repeat),
                    isError = mismatch,
                    supportingText = if (mismatch) stringResource(R.string.pin_mismatch) else null,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_save),
                enabled = valid,
                onClick = { onSave(pin) },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}

// Single reusable PIN gate dialog (TZ 3.6 / 4A). UI gate only — DB is not encrypted.
// The caller supplies the check; a wrong PIN shows an error and clears the field.
// message + confirmLabel/destructive turn the gate into a confirmation as well: deleting a
// delete-protected list asks what and confirms with the PIN in one dialog (TZ 3.2a).
@Composable
fun PinDialog(
    title: String,
    verify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmLabel: String? = null,
    destructive: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) Text(message)
                PinTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        wrong = false
                    },
                    label = stringResource(R.string.pin_label),
                    isError = wrong,
                    supportingText = if (wrong) stringResource(R.string.pin_wrong) else null,
                    autoFocus = true,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = confirmLabel ?: stringResource(R.string.action_ok),
                enabled = pin.isNotEmpty(),
                destructive = destructive,
                onClick = {
                    if (verify(pin)) {
                        onSuccess()
                    } else {
                        wrong = true
                        pin = ""
                    }
                },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.action_cancel), onDismiss)
        },
    )
}
