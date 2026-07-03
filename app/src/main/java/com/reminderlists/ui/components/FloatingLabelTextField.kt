package com.reminderlists.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType

// Single reusable text field with floating label + char counter (TZ 8).
// All app text inputs go through this component — no ad-hoc fields per screen.
// autoFocus: the first field of every input dialog/form focuses itself on open (user rule).
@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (maxLength == null || new.length <= maxLength) onValueChange(new)
        },
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = {
            val counter = maxLength?.let { "${value.length}/$it" }
            val text = listOfNotNull(supportingText, counter).joinToString("  ")
            if (text.isNotEmpty()) Text(text)
        },
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}
