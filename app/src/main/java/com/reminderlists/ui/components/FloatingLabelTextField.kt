package com.reminderlists.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

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
    password: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    autoFocus: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    // The counter shows only while the field is focused — an unfocused field takes no
    // supporting line, keeping stacked forms compact (user rule, TZ 8). An explicit
    // supportingText (hint/error) is always shown.
    val supporting = run {
        val counter = maxLength?.takeIf { focused }?.let { "${value.length}/$it" }
        listOfNotNull(supportingText, counter).joinToString("  ")
    }
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (maxLength == null || new.length <= maxLength) onValueChange(new)
        },
        label = { Text(label) },
        singleLine = singleLine,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = if (supporting.isEmpty()) {
            null
        } else {
            { Text(supporting) }
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
    )
}
