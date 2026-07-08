package com.reminderlists.util

// Central input limits (TZ 8) — surfaced as counters in the shared text field.
object Limits {
    const val NAME = 100 // folder / list / Title
    const val ITEM_TEXT = 200
    const val CONTENT = 2000
    const val COMMENT = 500
    const val TAG = 30
    const val TAGS_FIELD = 200 // whole comma-separated tags input (TZ 4.2)
    const val QUANTITY = 20
    const val UNIT = 20
    const val PIN = 8 // digits, list/note protection and the default PIN (TZ 3.6)

    const val MAX_PHOTOS = 10 // per item / reminder / note (TZ 3.3 / 4.2 / 4A.2)

    const val PRIORITY_MAX = 3 // «(–) ★ ★ ★ (+)» levels, 0 = none (TZ 4.2 п. 6)

    const val MISSED_GRACE_MINUTES = 10 // reminder catch-up grace window (TZ 4.10)

    const val MAX_SOUND_DURATION = 90 // Default sound loop cap, seconds (TZ 5)
    const val MAX_SOUND_LEVEL = 100 // Default sound volume 0..100 (TZ 5)

    // App-wide font scale factor (TZ 5 / 8); default is DEFAULT_FONT_SCALE.
    const val FONT_SCALE_MIN = 0.8f
    const val FONT_SCALE_MAX = 1.8f
}
