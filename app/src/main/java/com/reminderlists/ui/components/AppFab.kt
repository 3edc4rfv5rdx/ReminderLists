package com.reminderlists.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// onLongClick: e.g. the large font mode toggle on the list screen (TZ 3.5).
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    if (onLongClick == null) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = modifier,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        // FloatingActionButton has no long-press support — same look via Surface (M3 FAB
        // tokens: large shape, 56dp) with combinedClickable.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = contentDescription)
            }
        }
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
