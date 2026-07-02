package com.reminderlists.ui.screens.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState

// Lists tab (TZ 3): folders + root lists. Scaffold placeholder wired to nav/top bar/FAB.
@Composable
fun ListsScreen(navController: NavController, contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = { /* TODO menu: Settings/About/Dictionary/Backup (TZ 3.9) */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                },
            )
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                text = stringResource(R.string.empty_lists),
            )
        }
        FloatingActionButton(
            onClick = { /* TODO create list (TZ 3.9) */ },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_list))
        }
    }
}
