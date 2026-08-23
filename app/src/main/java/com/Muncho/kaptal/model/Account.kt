package com.Muncho.kaptal.model

import com.google.firebase.firestore.PropertyName

data class Account(
    val id: String = "",
    val name: String = "",
    val bankName: String = "",
    val initialBalance: Double = 0.0,
    val type: String = "CHECKING",
    
    @field:JvmField
    @get:PropertyName("isJoint")
    @set:PropertyName("isJoint")
    var isJoint: Boolean = false,

    val color: String = "#2196F3",
    val order: Int = 0,
    val members: List<String> = emptyList(),
    val ownerId: String = "",
    val linkedAccountId: String? = null,
    
    // --- MÉTADONNÉES CRÉDIT (Extraites du PDF) ---
    val loanNumber: String? = null,
    val insuranceRate: Double? = null,
    val loanStartDate: String? = null,
    val loanEndDate: String? = null,
    val totalAmount: Double? = null,
    val loanSigningDate: String? = null,
    val loanMonthlyPayment: Double? = null,
    val loanRate: Double? = null,
    val loanInsurance: Double? = null,
    val loanTotalRepayment: Double? = null, // Coût total (Mensualité x Durée)
    val cryptoSymbol: String? = null,      // Ex: BTC, ETH, SOL
    val initialInvestmentEur: Double? = null, // Coût d'achat du solde initial
    val savingsRate: Double? = null        // Taux d'intérêt pour les comptes rémunérés/courtage
)
