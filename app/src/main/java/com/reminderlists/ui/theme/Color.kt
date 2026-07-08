package com.reminderlists.ui.theme

import androidx.compose.ui.graphics.Color

// Base palette. Theme presets (TZ 5) are selected via AppTheme; all screens read colors
// through MaterialTheme, never hardcoded (TZ 8).
val Teal = Color(0xFF2E6C7E)
val TealDark = Color(0xFF1C4753)
val TealLight = Color(0xFF9CD3E0)

val Indigo = Color(0xFF3F51B5)
val Forest = Color(0xFF2E7D46)
val Plum = Color(0xFF6A3E7A)

// Warm presets (TZ 5): light seed (deeper, so light-theme content reads) + bright dark accent.
val Amber = Color(0xFFA67C00)
val Orange = Color(0xFFE65100)
val Beige = Color(0xFF9E8562)
val Olive = Color(0xFF6E7B2E)

// Pure black for light-scheme text/icons (TZ 8).
val Black = Color(0xFF000000)

// Dark theme (TZ 8): pure-black background/surfaces, white text, and a bright accent per
// preset — the raw seed is too dark to read on black, so dark mode uses these vivid tones.
val White = Color(0xFFFFFFFF)
val DarkSurface = Color(0xFF1C1C1C) // near-black elevated surface for chips/menus on black
val TealAccent = Color(0xFF4FD6E8)
val IndigoAccent = Color(0xFF9FA8FF)
val ForestAccent = Color(0xFF5CE08A)
val PlumAccent = Color(0xFFD69BEC)
val AmberAccent = Color(0xFFFFD54F)
val OrangeAccent = Color(0xFFFFB74D)
val BeigeAccent = Color(0xFFE8D3A0)
val OliveAccent = Color(0xFFC5D65C)

// Semantic snackbar colors (TZ 8): Error / Warning / Success / Info.
val SnackError = Color(0xFFC62828)
val SnackWarning = Color(0xFFEF6C00)
val SnackSuccess = Color(0xFF2E7D32)
val SnackInfo = Color(0xFF1565C0)

// Full-screen alert (TZ 4.5): a fixed high-visibility scheme, independent of the theme preset —
// an orange field with black controls (lock circle + buttons) and white lock/arrow/labels on them.
val AlertBackground = Color(0xFFEF6C00)
val AlertControl = Color(0xFF000000)
val AlertOnControl = Color(0xFFFFFFFF)
