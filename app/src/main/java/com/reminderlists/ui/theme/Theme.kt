package com.reminderlists.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Theme presets selectable in settings (TZ 5). Single source of truth for colors (TZ 8).
enum class AppTheme(val seed: androidx.compose.ui.graphics.Color) {
    TEAL(Teal),
    INDIGO(Indigo),
    FOREST(Forest),
    PLUM(Plum),
}

@Composable
fun ReminderListsTheme(
    theme: AppTheme = AppTheme.TEAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(primary = theme.seed)
    } else {
        lightColorScheme(primary = theme.seed)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
