package com.reminderlists.ui.screens.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.SettingEntity
import com.reminderlists.reminders.ReminderScheduler
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Settings screen state (TZ 5). Wired: default PIN, enable reminders, default sound
// Duration/level. The rest are still TODO.
class SettingsViewModel(
    private val db: AppDatabase,
    private val app: Application,
) : ViewModel() {

    private val settingsDao = db.settingsDao()

    // enable_reminders: absent means on (TZ 5 / 4.10).
    val enableReminders: StateFlow<Boolean> =
        settingsDao.observe(SettingsKeys.ENABLE_REMINDERS)
            .map { it != "0" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundDuration: StateFlow<Int> =
        settingsDao.observe(SettingsKeys.DEFAULT_SOUND_DURATION_SEC)
            .map { it?.toIntOrNull() ?: SettingsKeys.DEFAULT_SOUND_DURATION }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsKeys.DEFAULT_SOUND_DURATION)

    val soundLevel: StateFlow<Int> =
        settingsDao.observe(SettingsKeys.DEFAULT_SOUND_LEVEL)
            .map { it?.toIntOrNull() ?: SettingsKeys.DEFAULT_SOUND_LEVEL_VALUE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsKeys.DEFAULT_SOUND_LEVEL_VALUE)

    // Selected default sound Uri; null = the system default alarm ringtone (TZ 5).
    val defaultSoundUri: StateFlow<String?> =
        settingsDao.observe(SettingsKeys.DEFAULT_SOUND_URI)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current default PIN (TZ 3.6 / 5): changing/clearing it must be gated behind entering it,
    // else anyone could re-key the PIN that guards protected lists/notes. null = none set yet.
    val defaultPin: StateFlow<String?> =
        settingsDao.observe(SettingsKeys.DEFAULT_PIN)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // The sound list, preview and file-picker all live inside the shared SoundField (TZ 8).
    fun setDefaultSound(uri: String?) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.DEFAULT_SOUND_URI, uri))
        }
    }

    // Silence mode (TZ 5 / 4.10): off cancels armed alarms (cache kept), on re-arms them.
    fun setEnableReminders(enabled: Boolean) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.ENABLE_REMINDERS, if (enabled) "1" else "0"))
            if (enabled) ReminderScheduler.rearmAll(app, db) else ReminderScheduler.cancelAll(app, db)
        }
    }

    fun setSoundDuration(seconds: Int) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.DEFAULT_SOUND_DURATION_SEC, seconds.toString()))
        }
    }

    fun setSoundLevel(level: Int) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.DEFAULT_SOUND_LEVEL, level.toString()))
        }
    }

    // Default PIN for protected lists/notes (TZ 3.6 / 4A / 5); empty clears it.
    fun setDefaultPin(pin: String) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.DEFAULT_PIN, pin.takeIf { it.isNotEmpty() }))
        }
    }

    companion object {
        val Factory = appViewModelFactory { db, app -> SettingsViewModel(db, app) }
    }
}
