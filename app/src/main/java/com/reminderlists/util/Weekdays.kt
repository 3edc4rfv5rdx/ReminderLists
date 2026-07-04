package com.reminderlists.util

// Weekday bitmask helpers (TZ 6.2): bit0 = Mon .. bit6 = Sun.
object Weekdays {
    const val NONE = 0
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111

    fun has(mask: Int, day: Int): Boolean = mask and (1 shl day) != 0

    fun toggle(mask: Int, day: Int): Int = mask xor (1 shl day)

    // Preset range toggle (TZ 4.2 e′): a fully-on range switches off, otherwise on;
    // bits outside the range are kept (Weekdays leaves Sa/Su alone).
    fun toggleRange(mask: Int, range: Int): Int =
        if (mask and range == range) mask and range.inv() else mask or range

    // Compact card form "mtwt-ss": active day = letter, inactive = dash (TZ 4.6).
    private const val LETTERS = "mtwtfss"

    fun compact(mask: Int): String =
        LETTERS.mapIndexed { i, c -> if (has(mask, i)) c else '-' }.joinToString("")
}
