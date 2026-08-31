package com.muncho.kaptal.utils

class IosPdfImporter : PdfImporter {
    override suspend fun extractTableData(uri: String): List<PdfRow> = emptyList()
}

actual fun getPdfImporter(): PdfImporter = IosPdfImporter()
