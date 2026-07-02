package com.reminderlists.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.reminderlists.ui.theme.SnackError
import com.reminderlists.ui.theme.SnackInfo
import com.reminderlists.ui.theme.SnackSuccess
import com.reminderlists.ui.theme.SnackWarning
import androidx.compose.ui.graphics.Color

// Single semantic snackbar model (TZ 8): color + type icon (colorblind-safe).
enum class SnackType(val color: Color, val icon: ImageVector, val longDuration: Boolean) {
    ERROR(SnackError, Icons.Filled.Error, longDuration = true),
    WARNING(SnackWarning, Icons.Filled.Warning, longDuration = true),
    SUCCESS(SnackSuccess, Icons.Filled.CheckCircle, longDuration = false),
    INFO(SnackInfo, Icons.Filled.Info, longDuration = false),
}

data class SnackEvent(val type: SnackType, val message: String)
