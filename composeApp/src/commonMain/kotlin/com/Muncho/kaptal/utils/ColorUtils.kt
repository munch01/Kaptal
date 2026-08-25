package com.Muncho.kaptal.utils

import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String): Color {
    val cleanHex = if (hex.startsWith("#")) hex.substring(1) else hex
    return when (cleanHex.length) {
        6 -> {
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            Color(r, g, b)
        }
        8 -> {
            val a = cleanHex.substring(0, 2).toInt(16)
            val r = cleanHex.substring(2, 4).toInt(16)
            val g = cleanHex.substring(4, 6).toInt(16)
            val b = cleanHex.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        }
        else -> Color.Black
    }
}
