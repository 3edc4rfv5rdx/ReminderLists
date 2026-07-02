package com.reminderlists.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.ui.components.AppTopBar

// Settings (TZ 5): theme, language, dictionary, enable reminders, welcome, keep-screen-on,
// logs, time presets, default sound, default PIN, backup/restore. Placeholder list.
@Composable
fun SettingsScreen(navController: NavController) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.menu_settings),
            onBack = { navController.popBackStack() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // TODO wire each setting to SettingsDao (TZ 5).
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
                R.string.settings_default_pin,
                R.string.menu_backup_restore,
            ).forEach { res ->
                Text(stringResource(res), Modifier.padding(vertical = 12.dp))
            }
        }
    }
}
