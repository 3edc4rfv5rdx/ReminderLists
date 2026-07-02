package com.reminderlists.ui.screens.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.EmptyState

// Tag Filter (TZ 4.4): tag cloud sized by usage, combinable with Filters (logical AND).
@Composable
fun TagFilterScreen(navController: NavController) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_tag_filter),
            onBack = { navController.popBackStack() },
        )
        // TODO tag cloud with 4-5 font-size tiers by usage count (TZ 4.4).
        EmptyState(icon = Icons.Filled.Tag, text = stringResource(R.string.menu_tag_filter))
    }
}
