package com.example.kaptal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.kaptal.R

val LexendFamily = FontFamily(
    Font(R.font.lexend_thin, FontWeight.Thin),
    Font(R.font.lexend_extralight, FontWeight.ExtraLight),
    Font(R.font.lexend_light, FontWeight.Light),
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
    Font(R.font.lexend_bold, FontWeight.Bold),
    Font(R.font.lexend_extrabold, FontWeight.ExtraBold),
    Font(R.font.lexend_black, FontWeight.Black)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = LexendFamily, fontWeight = FontWeight.Medium)
)