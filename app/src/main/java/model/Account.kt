package com.example.kaptal.model

data class Account(
    val id: String = "",
    val name: String = "",
    val bankName: String = "",
    val initialBalance: Double = 0.0,
    val type: String = "CHECKING",
    val isJoint: Boolean = false,
    val currency: String = "€"
)