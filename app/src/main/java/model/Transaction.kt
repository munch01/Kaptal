package com.example.kaptal.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Transaction(
    @get:Exclude var id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val type: String = "EXPENSE", // "EXPENSE" ou "INCOME"

    // --- NOUVELLES CATÉGORIES ---
    val familyCategory: String = "Vital / Incompressible", // La grande famille
    val subCategory: String = "Alimentation",              // La sous-catégorie précise

    val paymentMethod: String = "CB",

    // --- POINTAGE INDÉPENDANT PAR MOIS (ex: ["2026-05", "2026-06"]) ---
    val checkedMonths: List<String> = emptyList(),

    // --- CORRECTION FIRESTORE POUR LES BOOLÉENS "IS..." ---
    @field:JvmField
    @get:PropertyName("isRecurring")
    @set:PropertyName("isRecurring")
    var isRecurring: Boolean = false,

    val recurrenceInterval: String? = null, // Ex: "MONTHLY"
    val recurrenceGroupId: String? = null,  // Pour lier les occurrences d'une même série
    val endDate: Timestamp? = null          // Date de fin optionnelle de la récurrence
) {
    /**
     * Vérifie si l'opération est pointée pour un mois donné (ex: "2026-08")
     */
    fun isCheckedForMonth(monthKey: String): Boolean {
        return checkedMonths.contains(monthKey)
    }
}