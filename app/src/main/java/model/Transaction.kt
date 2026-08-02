package com.example.kaptal.model

import com.google.firebase.Timestamp

data class Transaction(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val type: String = "EXPENSE", // "EXPENSE" ou "INCOME"
    val category: String = "Autre",
    val paymentMethod: String = "CB",
    val isChecked: Boolean = false,
    val isRecurring: Boolean = false,
    val recurrenceInterval: String? = null, // Ex: "MONTHLY"
    val recurrenceGroupId: String? = null,  // Pour lier les occurrences d'une même série
    val endDate: Timestamp? = null          // Date de fin optionnelle de la récurrence
)