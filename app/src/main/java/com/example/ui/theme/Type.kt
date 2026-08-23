package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
    /* Other default text styles to override
    ... (see VCS for full template) */
  )

/**
 * Bundled Noto Nastaliq Urdu (SIL Open Font License, downloaded from the
 * google/fonts repository). It is the authentic script for Urdu lyrics and is
 * applied automatically wherever a text contains Arabic-script characters.
 * Bundled in res/font on purpose: the app must stay 100% offline, so no
 * downloadable-fonts / Play-Services font fetch is ever used.
 */
val NastaliqFamily = FontFamily(
    Font(R.font.noto_nastaliq_urdu, FontWeight.Normal)
)
