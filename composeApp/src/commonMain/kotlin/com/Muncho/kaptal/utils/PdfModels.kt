package com.Muncho.kaptal.utils

import kotlinx.serialization.Serializable

@Serializable
data class PdfCell(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    var colIndex: Int = -1,
    val page: Int = 0
)

@Serializable
data class PdfRow(
    val cells: List<PdfCell>
)
