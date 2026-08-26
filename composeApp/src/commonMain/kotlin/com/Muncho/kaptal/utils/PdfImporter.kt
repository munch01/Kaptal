package com.muncho.kaptal.utils

interface PdfImporter {
    suspend fun extractTableData(uri: String): List<PdfRow>
}

expect fun getPdfImporter(): PdfImporter
