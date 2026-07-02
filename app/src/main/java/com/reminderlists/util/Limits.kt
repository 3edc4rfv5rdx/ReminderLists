package com.reminderlists.util

// Central input limits (TZ 8) — surfaced as counters in the shared text field.
object Limits {
    const val NAME = 100 // folder / list / Title
    const val ITEM_TEXT = 200
    const val CONTENT = 2000
    const val COMMENT = 500
    const val TAG = 30
    const val QUANTITY = 20
    const val UNIT = 20

    const val MAX_PHOTOS = 10 // per item / reminder / note (TZ 3.3 / 4.2 / 4A.2)

    const val MISSED_GRACE_MINUTES = 10 // reminder catch-up grace window (TZ 4.10)
}
