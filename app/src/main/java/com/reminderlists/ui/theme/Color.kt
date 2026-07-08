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

// Pure black for light-scheme text/icons (TZ 8).
val Black = Color(0xFF000000)

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
