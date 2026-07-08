package com.reminderlists.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

// Central typography (TZ 8). Every style is the Material default scaled by a factor the user
// sets in Settings (TZ 5); one number resizes the whole app. Range/default live in Limits.
const val DEFAULT_FONT_SCALE = 1.2f

private fun TextStyle.scaled(factor: Float) = copy(
    fontSize = if (fontSize.isSpecified) (fontSize.value * factor).sp else fontSize,
    lineHeight = if (lineHeight.isSpecified) (lineHeight.value * factor).sp else lineHeight,
)

fun appTypography(scale: Float = DEFAULT_FONT_SCALE): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.scaled(scale),
        displayMedium = displayMedium.scaled(scale),
        displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale),
        headlineMedium = headlineMedium.scaled(scale),
        headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale),
        titleMedium = titleMedium.scaled(scale),
        titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale),
        bodyMedium = bodyMedium.scaled(scale),
        bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale),
        labelMedium = labelMedium.scaled(scale),
        labelSmall = labelSmall.scaled(scale),
    )
}

val LargeItemTextStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.Medium,
)
