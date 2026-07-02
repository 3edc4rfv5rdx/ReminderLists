package com.reminderlists.ui.screens.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
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

// Notes tab (TZ 4A): folders + root note cards, no firing. Own filter state + indicator.
@Composable
fun NotesScreen(navController: NavController, contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    // TODO filter indicator All/T/F/TF (TZ 3.9)
                    IconButton(onClick = { /* TODO menu: Filters/Tag Filter/Clear/Settings/About */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                },
            )
            EmptyState(
                icon = Icons.Filled.Notes,
                text = stringResource(R.string.empty_notes),
            )
        }
        FloatingActionButton(
            onClick = { /* TODO new note (TZ 3.9) */ },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_note))
        }
    }
}
