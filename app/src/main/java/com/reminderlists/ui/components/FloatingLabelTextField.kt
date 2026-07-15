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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

private val NEWLINE_REGEX = Regex("\\R")

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
    maxLines: Int = Int.MAX_VALUE,
    // Wrapping fields that are still one logical line (item Name): the text flows onto
    // several lines, but line breaks never become part of the value.
    allowNewlines: Boolean = true,
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
        onValueChange = { raw ->
            // Pasted line breaks collapse to spaces, so the value stays one line.
            val new = if (allowNewlines) raw else raw.replace(NEWLINE_REGEX, " ")
            // Block growth past the limit, but always allow shrinking — otherwise a value that
            // is already over the limit (e.g. imported data) can never be shortened or edited.
            if (maxLength == null || new.length <= maxLength || new.length < value.length) {
                onValueChange(new)
            }
        },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        // A Done action replaces the IME's Enter key, so a wrapping one-line field
        // cannot get a break typed into it either.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (allowNewlines) ImeAction.Default else ImeAction.Done,
        ),
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
