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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

// Semantic snackbar kind (TZ 8): the color + icon (colorblind-safe). Default glow is 4s;
// a SnackEvent may override it via durationMs.
enum class SnackType(val color: Color, val icon: ImageVector) {
    ERROR(SnackError, Icons.Filled.Error),
    WARNING(SnackWarning, Icons.Filled.Warning),
    SUCCESS(SnackSuccess, Icons.Filled.CheckCircle),
    INFO(SnackInfo, Icons.Filled.Info),
    ;

    companion object {
        const val DEFAULT_DURATION_MS = 4000L
    }
}

// One snackbar to show: kind (color/icon), text, and an optional glow duration override. Use
// the four semantic factories — info (blue), success (green), warning (orange), error (red).
data class SnackEvent(val type: SnackType, val message: String, val durationMs: Long? = null) {
    companion object {
        fun info(message: String, durationMs: Long? = null) = SnackEvent(SnackType.INFO, message, durationMs)
        fun success(message: String, durationMs: Long? = null) = SnackEvent(SnackType.SUCCESS, message, durationMs)
        fun warning(message: String, durationMs: Long? = null) = SnackEvent(SnackType.WARNING, message, durationMs)
        fun error(message: String, durationMs: Long? = null) = SnackEvent(SnackType.ERROR, message, durationMs)
    }
}

// App-wide snackbar host (TZ 8): tab screens publish here so the single bar is hosted once in
// AppRoot and draws above the bottom navigation bar (by Z), not hidden behind it. Semantic
// helpers mirror the SnackEvent factories.
class SnackController {
    var event by mutableStateOf<SnackEvent?>(null)
        private set

    fun show(event: SnackEvent) { this.event = event }

    fun info(message: String, durationMs: Long? = null) = show(SnackEvent.info(message, durationMs))
    fun success(message: String, durationMs: Long? = null) = show(SnackEvent.success(message, durationMs))
    fun warning(message: String, durationMs: Long? = null) = show(SnackEvent.warning(message, durationMs))
    fun error(message: String, durationMs: Long? = null) = show(SnackEvent.error(message, durationMs))

    fun dismiss() { event = null }
}

val LocalSnackController = staticCompositionLocalOf<SnackController?> { null }

// Single snackbar presenter (TZ 8): semantic color + type icon, auto-dismisses after a
// type-dependent duration. The caller positions it (usually the bottom of a Box).
@Composable
fun AppSnackbar(event: SnackEvent?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val current = event ?: return
    LaunchedEffect(current) {
        delay(current.durationMs ?: SnackType.DEFAULT_DURATION_MS)
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
