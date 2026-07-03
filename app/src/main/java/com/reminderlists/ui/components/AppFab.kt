package com.reminderlists.ui.components

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Shared FABs (TZ 8): theme primary background via design tokens, one component app-wide.

// FAB level shared by all screens (TZ 8): the bottom bar is measured once at startup
// (see AppRoot) and every FAB is drawn immediately at that distance from the window
// bottom — with or without the bar, so the button never jumps between screens.
object FabLevel {
    // Measured NavigationBar height incl. its system inset; fallback = M3 default.
    var barHeight by mutableStateOf(80.dp)
}

@Composable
fun AppFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

// Secondary smaller FAB (e.g. "new folder" — TZ 3.9).
@Composable
fun AppSmallFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
