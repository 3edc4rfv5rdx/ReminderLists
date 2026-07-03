package com.reminderlists.ui.screens.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.FloatingLabelTextField
import com.reminderlists.util.Limits

// Item add/edit window (TZ 3.3): Name / Quantity / Unit, up-bar with Back and Save.
// Photo button (shared photo module) and Name autocomplete (TZ 3.4) arrive with their features.
@Composable
fun ItemEditorScreen(navController: NavController, listId: Long, itemId: Long) {
    val vm: ItemEditorViewModel = viewModel(factory = ItemEditorViewModel.factory(listId, itemId))

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(if (vm.isEdit) R.string.action_edit else R.string.fab_new_item),
            onBack = { navController.popBackStack() },
            actions = {
                IconButton(
                    enabled = vm.name.isNotBlank(),
                    onClick = { vm.save { navController.popBackStack() } },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            FloatingLabelTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = stringResource(R.string.field_name),
                maxLength = Limits.ITEM_TEXT,
                autoFocus = true,
            )
            FloatingLabelTextField(
                value = vm.quantity,
                onValueChange = { vm.quantity = it },
                label = stringResource(R.string.field_quantity),
                maxLength = Limits.QUANTITY,
                modifier = Modifier.padding(top = 8.dp),
            )
            FloatingLabelTextField(
                value = vm.unit,
                onValueChange = { vm.unit = it },
                label = stringResource(R.string.field_unit),
                maxLength = Limits.UNIT,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
