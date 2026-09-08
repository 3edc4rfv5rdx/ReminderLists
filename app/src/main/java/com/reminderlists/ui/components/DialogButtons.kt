package com.reminderlists.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Shared dialog buttons (TZ 8): every dialog button is filled with a theme color —
// confirm on primary (error when destructive), dismiss on secondary. No text-only buttons.

// Tighter than the Material default (24.dp horizontal): with two filled buttons the default
// padding overflows a narrow dialog and AlertDialog wraps them onto two lines.
private val DialogButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    DialogButton(
        text = text,
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
    )
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    DialogButton(
        text = text,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    )
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    colors: ButtonColors,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = DialogButtonPadding,
    ) {
        Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
