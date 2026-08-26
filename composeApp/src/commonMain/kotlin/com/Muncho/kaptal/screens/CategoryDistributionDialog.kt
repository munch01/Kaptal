package com.muncho.kaptal.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.utils.DateTimeUtils
import kotlinx.datetime.Instant
import kotlin.math.abs

@Composable
fun CategoryDistributionDialog(
    transactions: List<Transaction>,
    year: Int,
    month: Int,
    onDismiss: () -> Unit
) {
    val activeTransactions = remember(transactions, year, month) {
        transactions.filter { tx -> isTransactionActiveInMonth(tx, year, month) }
    }

    val totalExpenses = remember(activeTransactions) {
        activeTransactions.filter { it.amount < 0 }.sumOf { abs(it.amount) }
    }

    val categoryHierarchy = remember(activeTransactions, totalExpenses) {
        val expenses = activeTransactions.filter { it.amount < 0 }

        expenses.groupBy { it.familyCategory?.ifEmpty { "Autre" } ?: "Autre" }
            .map { (family, familyTxList) ->
                val familyTotal = familyTxList.sumOf { abs(it.amount) }
                val familyPercentage = if (totalExpenses > 0) (familyTotal / totalExpenses) * 100 else 0.0

                val subCategories = familyTxList.groupBy { it.subCategory?.ifEmpty { "Autre" } ?: "Autre" }
                    .map { (sub, subTxList) ->
                        val subTotal = subTxList.sumOf { abs(it.amount) }
                        val subPercentageOfFamily = if (familyTotal > 0) (subTotal / familyTotal) * 100 else 0.0
                        Triple(sub, subTotal, subPercentageOfFamily)
                    }
                    .sortedByDescending { it.second }

                Quadruple(family, familyTotal, familyPercentage, subCategories)
            }
            .sortedByDescending { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analyse du mois", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Dépenses totales", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${totalExpenses.roundTo(2)} €",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(DateTimeUtils.formatDate(DateTimeUtils.startOfMonth(DateTimeUtils.now()), "MMM yyyy"), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (categoryHierarchy.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Aucune dépense ce mois-ci", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryHierarchy) { (family, familyAmount, familyPercentage, subCategories) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(family, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${familyAmount.roundTo(2)} € (${familyPercentage.roundTo(1)}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { (familyPercentage / 100).toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                                    color = getCategoryIndicatorColor(family),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    subCategories.forEach { (sub, subAmt, _) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("• $sub", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${subAmt.roundTo(2)} €", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Fermer")
            }
        }
    )
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

internal fun isTransactionActiveInMonth(tx: Transaction, year: Int, month: Int): Boolean {
    val txInstant = tx.date.toInstant()
    val targetIndex = year * 12 + month
    val startIndex = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)

    return if (tx.isRecurring) {
        val startsBeforeOrDuring = startIndex <= targetIndex
        val endsAfterOrDuring = if (tx.endDate != null) {
            val endInstant = tx.endDate.toInstant()
            targetIndex < (DateTimeUtils.getYear(endInstant) * 12 + DateTimeUtils.getMonth(endInstant))
        } else true
        startsBeforeOrDuring && endsAfterOrDuring
    } else {
        startIndex == targetIndex
    }
}

@Composable
internal fun getCategoryIndicatorColor(family: String?): Color {
    val cleanFamily = family?.lowercase()?.trim() ?: ""
    return when {
        cleanFamily.contains("vital") || cleanFamily.contains("incompressible") -> Color(0xFF2E7D32)
        cleanFamily.contains("confort") || cleanFamily.contains("vie courante") -> Color(0xFF1976D2)
        cleanFamily.contains("superficiel") || cleanFamily.contains("plaisir") -> Color(0xFFE65100)
        cleanFamily.contains("salaire") || cleanFamily.contains("revenu") -> Color(0xFF388E3C)
        else -> MaterialTheme.colorScheme.secondary
    }
}

private fun dev.gitlive.firebase.firestore.Timestamp.toInstant(): Instant = 
    Instant.fromEpochSeconds(this.seconds, this.nanoseconds)

private fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}
