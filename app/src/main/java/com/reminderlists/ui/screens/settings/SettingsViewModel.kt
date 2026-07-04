package com.reminderlists.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.dao.SettingsDao
import com.reminderlists.data.db.entity.SettingEntity
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.launch

// Settings screen state (TZ 5). Only the wired settings live here; the rest are TODO.
class SettingsViewModel(private val settingsDao: SettingsDao) : ViewModel() {

    // Default PIN for protected lists/notes (TZ 3.6 / 4A / 5); empty clears it.
    fun setDefaultPin(pin: String) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.DEFAULT_PIN, pin.takeIf { it.isNotEmpty() }))
        }
    }

    companion object {
        val Factory = appViewModelFactory { db, _ -> SettingsViewModel(db.settingsDao()) }
    }
}
