package com.muncho.kaptal.model

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String = "",
    val name: String = "",
    val bankName: String = "",
    val initialBalance: Double = 0.0,
    val type: String = "CHECKING",
    val isJoint: Boolean = false,
    val color: String = "#2196F3",
    val order: Int = 0,
    val members: List<String> = emptyList(),
    val ownerId: String = "",
    val linkedAccountId: String? = null,
    
    // --- MÉTADONNÉES CRÉDIT ---
    val loanNumber: String? = null,
    val insuranceRate: Double? = null,
    val loanStartDate: String? = null,
    val loanEndDate: String? = null,
    val totalAmount: Double? = null,
    val loanSigningDate: String? = null,
    val loanMonthlyPayment: Double? = null,
    val loanRate: Double? = null,
    val loanInsurance: Double? = null,
    val loanTotalRepayment: Double? = null,
    val cryptoSymbol: String? = null,
    val initialInvestmentEur: Double? = null,
    val savingsRate: Double? = null
)
