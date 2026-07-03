package com.reminderlists.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reminderlists.R

// Shared swipe gestures for record rows (TZ 8): swipe right = edit, swipe left = delete.
// The row is not dismissed — the action fires once and the row snaps back.
@Composable
fun SwipeActionsRow(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // confirmValueChange is deprecated: react to the settled value and snap the row back.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> {}
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
        SwipeToDismissBoxValue.EndToStart -> Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 24.dp),
            )
        }
        SwipeToDismissBoxValue.Settled -> {}
    }
}
