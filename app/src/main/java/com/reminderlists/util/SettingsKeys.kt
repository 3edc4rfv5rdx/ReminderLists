package com.reminderlists.util

// Keys for the settings table (TZ 5 / 6.3).
object SettingsKeys {
    const val THEME = "theme"
    const val LANGUAGE = "language"
    const val DEFAULT_PIN = "default_pin"
    const val ENABLE_REMINDERS = "enable_reminders"
    const val KEEP_SCREEN_ON_LARGE_FONT = "keep_screen_on_large_font"
    const val WRITE_LOGS_TO_FILE = "write_logs_to_file"
    const val TIME_PRESET_MORNING = "time_preset_morning"
    const val TIME_PRESET_DAY = "time_preset_day"
    const val TIME_PRESET_EVENING = "time_preset_evening"
    const val DEFAULT_SOUND_URI = "default_sound_uri"
    const val DEFAULT_SOUND_DURATION_SEC = "default_sound_duration_sec"
    const val DEFAULT_SOUND_LEVEL = "default_sound_level"
    const val WELCOME_SHOWN = "welcome_shown"

    // Defaults (TZ 5).
    const val DEFAULT_MORNING = "09:30"
    const val DEFAULT_DAY = "12:30"
    const val DEFAULT_EVENING = "18:30"
    const val DEFAULT_SOUND_DURATION = 10 // seconds, range 0..180
    const val DEFAULT_SOUND_LEVEL_VALUE = 50 // range 0..100
}
