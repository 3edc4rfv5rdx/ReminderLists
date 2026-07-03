package com.reminderlists.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Central typography (TZ 8). The large-font list mode (TZ 3.5) uses LargeItemTextStyle.
val AppTypography = Typography()

val LargeItemTextStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.Medium,
)
