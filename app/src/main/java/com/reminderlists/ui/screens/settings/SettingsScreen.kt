package com.reminderlists.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.PinSetupDialog

// Settings (TZ 5): theme, language, dictionary, enable reminders, welcome, keep-screen-on,
// logs, time presets, default sound, default PIN, backup/restore. Default PIN is wired;
// the rest are placeholders.
@Composable
fun SettingsScreen(navController: NavController) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    var pinDialogOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_settings),
            onBack = { navController.popBackStack() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // TODO wire the remaining settings to SettingsDao (TZ 5).
            listOf(
                R.string.settings_theme,
                R.string.settings_language,
                R.string.menu_dictionary,
                R.string.settings_enable_reminders,
                R.string.settings_welcome_screen,
                R.string.settings_keep_screen_on,
                R.string.settings_write_logs,
                R.string.settings_time_presets,
                R.string.settings_default_sound,
            ).forEach { res ->
                Text(stringResource(res), Modifier.padding(vertical = 12.dp))
            }
            Text(
                text = stringResource(R.string.settings_default_pin),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pinDialogOpen = true }
                    .padding(vertical = 12.dp),
            )
            Text(stringResource(R.string.menu_backup_restore), Modifier.padding(vertical = 12.dp))
        }
    }

    // Default PIN editor (TZ 5): masked, typed twice, never shown back; saving an
    // empty value clears the PIN.
    if (pinDialogOpen) {
        PinSetupDialog(
            title = stringResource(R.string.settings_default_pin),
            emptyPinAllowed = true,
            onSave = { pin ->
                vm.setDefaultPin(pin)
                pinDialogOpen = false
            },
            onDismiss = { pinDialogOpen = false },
        )
    }
}
