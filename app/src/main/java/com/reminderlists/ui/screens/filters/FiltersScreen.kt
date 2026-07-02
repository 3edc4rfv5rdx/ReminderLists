package com.reminderlists.ui.screens.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList

// Filters (TZ 4.3): Date from/to, Tags, Priority. Shared by Reminders and Notes tabs.
@Composable
fun FiltersScreen(navController: NavController) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_filters),
            onBack = { navController.popBackStack() },
        )
        // TODO date-range + tags + priority fields with validation (TZ 4.3).
        EmptyState(icon = Icons.Filled.FilterList, text = stringResource(R.string.menu_filters))
    }
}
