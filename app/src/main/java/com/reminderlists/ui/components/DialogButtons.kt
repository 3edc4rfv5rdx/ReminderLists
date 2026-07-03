package com.reminderlists.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// Shared dialog buttons (TZ 8): every dialog button is filled with a theme color —
// confirm on primary (error when destructive), dismiss on secondary. No text-only buttons.

@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) { Text(text) }
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) { Text(text) }
}
