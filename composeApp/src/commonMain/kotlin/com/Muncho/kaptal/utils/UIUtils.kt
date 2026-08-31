package com.muncho.kaptal.utils

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.*
import kotlin.math.roundToInt

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
    // Format to 2 decimal places manually for KMP common
    val rounded = (amount * 100).roundToInt() / 100.0
    val parts = rounded.toString().split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1].padEnd(2, '0').substring(0, 2) else "00"
    return "$integerPart,$decimalPart €"
}

fun formatDecimal(value: Double, precision: Int): String {
    val factor = 10.0.pow(precision)
    val rounded = (value * factor).roundToInt() / factor
    return rounded.toString().replace(".", ",")
}

private fun Double.pow(n: Int): Double {
    var res = 1.0
    repeat(n) { res *= this }
    return res
}
