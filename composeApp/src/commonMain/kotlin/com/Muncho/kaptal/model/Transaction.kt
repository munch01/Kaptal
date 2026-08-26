package com.muncho.kaptal.model

import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Transaction(
    @Transient var id: String = "",
    val title: String? = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val type: String = "EXPENSE",
    val familyCategory: String? = "Vital / Incompressible",
    val subCategory: String? = "Alimentation",
    val paymentMethod: String = "CB",
    val checkedMonths: List<String> = emptyList(),
    var isRecurring: Boolean = false,
    val recurrenceInterval: String? = null,
    val recurrenceGroupId: String? = null,
    val endDate: Timestamp? = null,
    val transferGroupId: String? = null,
    val targetAccountId: String? = null,
    val principalPart: Double? = null,
    val interestPart: Double? = null,
    val insurancePart: Double? = null,
    val remainingDebt: Double? = null,
    val investmentEur: Double? = null,
    val feesPercent: Double? = null
) {
    fun isCheckedForMonth(monthKey: String): Boolean {
        return checkedMonths.contains(monthKey)
    }
}
