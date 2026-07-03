package com.reminderlists.ui.screens.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState

// Opened list screen (TZ 3.2). Placeholder body: items (TZ 3.3) are the next feature.
@Composable
fun ListDetailScreen(navController: NavController, listId: Long) {
    val vm: ListDetailViewModel = viewModel(factory = ListDetailViewModel.factory(listId))
    val list by vm.list.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = list?.name.orEmpty(),
                onBack = { navController.popBackStack() },
                // TODO in-list menu (TZ 3.2): Move, Share, Comment, Delete checked, Uncheck all.
            )
            val comment = list?.comment
            if (comment != null) {
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                text = stringResource(R.string.empty_items),
            )
        }
        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_item),
            onClick = { /* TODO add item (TZ 3.3), long-press = large font mode (TZ 3.5) */ },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}
