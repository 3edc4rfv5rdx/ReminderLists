package com.reminderlists.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Readable content colour for a filled accent: black on a light accent (amber/beige),
// white on a dark one — keeps FAB/label/icon text legible on any preset (TZ 8).
private fun onAccentColor(c: Color): Color = if (c.luminance() > 0.5f) Black else White

// Theme presets selectable in settings (TZ 5). Single source of truth for colors (TZ 8).
// seed = light-theme accent; accentDark = bright accent for the black dark theme.
enum class AppTheme(
    val seed: Color,
    val accentDark: Color,
) {
    TEAL(Teal, TealAccent),
    INDIGO(Indigo, IndigoAccent),
    FOREST(Forest, ForestAccent),
    PLUM(Plum, PlumAccent),
    AMBER(Amber, AmberAccent),
    ORANGE(Orange, OrangeAccent),
    BEIGE(Beige, BeigeAccent),
    OLIVE(Olive, OliveAccent);

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
        // Dark scheme: true black (not M3's dark grey), white text, and a bright accent that
        // reads on black (TZ 8). All accent roles use the same vivid tone with black content;
        // surfaceVariant is a near-black elevated surface so chips/menus stay visible.
        val accent = theme.accentDark
        // Content on the accent adapts to its brightness so a light accent (amber/beige)
        // gets black text, a dark one white — no per-preset content colour needed.
        val onAccent = onAccentColor(accent)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent,
            onPrimaryContainer = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            tertiary = accent,
            onTertiary = onAccent,
            background = Black,
            onBackground = White,
            surface = Black,
            onSurface = White,
            surfaceVariant = DarkSurface,
            onSurfaceVariant = White,
        )
    } else {
        // Light scheme: pure-black text/icons for maximum contrast (TZ 8), tinted only by primary.
        // outline is black too so low-emphasis labels/icons and hints aren't faded grey; spots
        // that must stay pale (empty priority stars, disabled tint) use outlineVariant instead.
        lightColorScheme(
            primary = theme.seed,
            onPrimary = onAccentColor(theme.seed),
            onBackground = Black,
            onSurface = Black,
            onSurfaceVariant = Black,
            outline = Black,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontScale),
        content = content,
    )
}
