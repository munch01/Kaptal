package com.example.kaptal.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Account(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val type: String = "CHECKING", // CHECKING, SAVINGS, etc.
    val initialBalance: Double = 0.0,
    val currency: String = "EUR",
    @ServerTimestamp
    val createdAt: Date? = null
)