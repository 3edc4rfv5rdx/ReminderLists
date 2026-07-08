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
    PLUM(Plum);

    companion object {
        // Stored as the enum name; unknown/absent falls back to the default (TZ 5).
        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.name == key } ?: TEAL
    }
}

// Light/Dark/System selector (TZ 5). Default is LIGHT — the app does not follow the system
// dark theme unless the user picks SYSTEM.
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name == key } ?: LIGHT
    }
}

@Composable
fun ReminderListsTheme(
    theme: AppTheme = AppTheme.TEAL,
    mode: ThemeMode = ThemeMode.LIGHT,
    fontScale: Float = DEFAULT_FONT_SCALE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) {
        darkColorScheme(primary = theme.seed)
    } else {
        // Light scheme: pure-black text/icons for maximum contrast (TZ 8), tinted only by primary.
        lightColorScheme(
            primary = theme.seed,
            onBackground = Black,
            onSurface = Black,
            onSurfaceVariant = Black,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontScale),
        content = content,
    )
}
