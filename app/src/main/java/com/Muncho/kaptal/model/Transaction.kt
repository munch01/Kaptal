package com.Muncho.kaptal.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Transaction(
    @get:Exclude var id: String = "",
    val title: String? = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val type: String = "EXPENSE", // "EXPENSE" ou "INCOME"

    // --- NOUVELLES CATÉGORIES ---
    val familyCategory: String? = "Vital / Incompressible", // La grande famille
    val subCategory: String? = "Alimentation",              // La sous-catégorie précise

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
    val endDate: Timestamp? = null,         // Date de fin optionnelle de la récurrence

    // --- VIREMENTS ENTRE COMPTES ---
    val transferGroupId: String? = null,    // Identifiant unique pour lier les deux côtés du virement
    val targetAccountId: String? = null,     // ID du compte de l'autre côté du virement

    // --- DÉTAILS CRÉDIT ---
    val principalPart: Double? = null,      // Part de capital remboursé
    val interestPart: Double? = null,       // Part d'intérêts payés
    val insurancePart: Double? = null,      // Part d'assurance payée
    val remainingDebt: Double? = null,      // Capital restant dû après cette mensualité
    val investmentEur: Double? = null       // Montant investi en euros (pour les comptes Crypto)
) {
    /**
     * Vérifie si l'opération est pointée pour un mois donné (ex: "2026-08")
     */
    fun isCheckedForMonth(monthKey: String): Boolean {
        return checkedMonths.contains(monthKey)
    }
}