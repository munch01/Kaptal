package com.example.kaptal.model

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
    val linkedAccountId: String? = null
)
