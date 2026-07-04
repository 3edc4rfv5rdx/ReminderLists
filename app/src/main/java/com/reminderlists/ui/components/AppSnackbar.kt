package com.reminderlists.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.reminderlists.ui.theme.SnackError
import com.reminderlists.ui.theme.SnackInfo
import com.reminderlists.ui.theme.SnackSuccess
import com.reminderlists.ui.theme.SnackWarning
import kotlinx.coroutines.delay

// Single semantic snackbar model (TZ 8): color + type icon (colorblind-safe).
enum class SnackType(val color: Color, val icon: ImageVector, val longDuration: Boolean) {
    ERROR(SnackError, Icons.Filled.Error, longDuration = true),
    WARNING(SnackWarning, Icons.Filled.Warning, longDuration = true),
    SUCCESS(SnackSuccess, Icons.Filled.CheckCircle, longDuration = false),
    INFO(SnackInfo, Icons.Filled.Info, longDuration = false),
}

data class SnackEvent(val type: SnackType, val message: String)

// Single snackbar presenter (TZ 8): semantic color + type icon, auto-dismisses after a
// type-dependent duration. The caller positions it (usually the bottom of a Box).
@Composable
fun AppSnackbar(event: SnackEvent?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val current = event ?: return
    LaunchedEffect(current) {
        delay(if (current.type.longDuration) 5000L else 2500L)
        onDismiss()
    }
    Surface(
        color = current.type.color,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(current.type.icon, contentDescription = null)
            Text(current.message, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
