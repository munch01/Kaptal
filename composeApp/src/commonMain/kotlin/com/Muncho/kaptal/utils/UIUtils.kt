package com.Muncho.kaptal.utils

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.*

fun getCategoryIndicatorColor(family: String?): Color {
    return when (family) {
        "Vital / Incompressible" -> Color(0xFFF44336)
        "Confort / Vie courante" -> Color(0xFF2196F3)
        "Superficiel / Plaisir" -> Color(0xFF4CAF50)
        "Recettes" -> Color(0xFF2E7D32)
        "Virement" -> Color(0xFF9C27B0)
        "Crédit" -> Color(0xFF795548)
        else -> Color(0xFF9E9E9E)
    }
}

fun getMonthName(year: Int, month: Int): String {
    val monthName = when (month) {
        0 -> "Janvier"
        1 -> "Février"
        2 -> "Mars"
        3 -> "Avril"
        4 -> "Mai"
        5 -> "Juin"
        6 -> "Juillet"
        7 -> "Août"
        8 -> "Septembre"
        9 -> "Octobre"
        10 -> "Novembre"
        11 -> "Décembre"
        else -> ""
    }
    return "$monthName $year"
}

fun formatAmount(amount: Double): String {
    // Basic formatting for KMP common
    val rounded = (amount * 100).toLong() / 100.0
    return "$rounded €"
}
