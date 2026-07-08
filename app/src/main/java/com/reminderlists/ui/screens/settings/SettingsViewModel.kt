package com.reminderlists.ui.screens.settings

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.SettingEntity
import com.reminderlists.reminders.ReminderScheduler
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.ui.theme.AppTheme
import com.reminderlists.ui.theme.DEFAULT_FONT_SCALE
import com.reminderlists.ui.theme.ThemeMode
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Settings screen state (TZ 5).
class SettingsViewModel(
    private val db: AppDatabase,
    private val app: Application,
) : ViewModel() {

    private val settingsDao = db.settingsDao()

    // Theme color preset and Light/Dark/System mode (TZ 5); defaults Teal + Light.
    val themeColor: StateFlow<AppTheme> =
        settingsDao.observe(SettingsKeys.THEME)
            .map { AppTheme.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.TEAL)

    val themeMode: StateFlow<ThemeMode> =
        settingsDao.observe(SettingsKeys.THEME_MODE)
            .map { ThemeMode.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.LIGHT)

    // App-wide font scale (TZ 5); default DEFAULT_FONT_SCALE.
    val fontScale: StateFlow<Float> =
        settingsDao.observe(SettingsKeys.FONT_SCALE)
            .map { it?.toFloatOrNull() ?: DEFAULT_FONT_SCALE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_FONT_SCALE)

    // Keep screen on in large-font mode (TZ 3.5 / 5), default ON; "false" is the off sentinel.
    val keepScreenOn: StateFlow<Boolean> =
        settingsDao.observe(SettingsKeys.KEEP_SCREEN_ON_LARGE_FONT)
            .map { it != "false" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Write debug logs to file (TZ 5 / 8), default OFF.
    val writeLogs: StateFlow<Boolean> =
        settingsDao.observe(SettingsKeys.WRITE_LOGS_TO_FILE)
            .map { it == "1" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Time presets used by the reminder form (TZ 5 / 4.2); "HH:mm" strings.
    val morningPreset: StateFlow<String> = presetFlow(SettingsKeys.TIME_PRESET_MORNING, SettingsKeys.DEFAULT_MORNING)
    val dayPreset: StateFlow<String> = presetFlow(SettingsKeys.TIME_PRESET_DAY, SettingsKeys.DEFAULT_DAY)
    val eveningPreset: StateFlow<String> = presetFlow(SettingsKeys.TIME_PRESET_EVENING, SettingsKeys.DEFAULT_EVENING)

    private fun presetFlow(key: String, default: String): StateFlow<String> =
        settingsDao.observe(key)
            .map { it ?: default }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

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

    fun setThemeColor(theme: AppTheme) {
        viewModelScope.launch { settingsDao.put(SettingEntity(SettingsKeys.THEME, theme.name)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDao.put(SettingEntity(SettingsKeys.THEME_MODE, mode.name)) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsDao.put(SettingEntity(SettingsKeys.FONT_SCALE, scale.toString())) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.KEEP_SCREEN_ON_LARGE_FONT, if (enabled) "true" else "false"))
        }
    }

    fun setWriteLogs(enabled: Boolean) {
        Logger.writeToFile = enabled // take effect immediately, not just next launch
        viewModelScope.launch {
            settingsDao.put(SettingEntity(SettingsKeys.WRITE_LOGS_TO_FILE, if (enabled) "1" else "0"))
        }
    }

    fun setTimePreset(key: String, time: String) {
        viewModelScope.launch { settingsDao.put(SettingEntity(key, time)) }
    }

    // UI language (TZ 5). Per-app locale via the framework LocaleManager (API 33+, pure AOSP);
    // the system persists it and recreates the activity. null tag = follow the system language.
    fun currentLanguageTag(): String? =
        app.getSystemService(LocaleManager::class.java)
            .applicationLocales
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.language

    fun setLanguage(tag: String?) {
        app.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    companion object {
        val Factory = appViewModelFactory { db, app -> SettingsViewModel(db, app) }
    }
}
