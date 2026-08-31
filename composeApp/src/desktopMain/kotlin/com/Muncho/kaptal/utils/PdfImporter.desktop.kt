package com.muncho.kaptal.utils

class DesktopPdfImporter : PdfImporter {
    override suspend fun extractTableData(uri: String): List<PdfRow> = emptyList()
}

actual fun getPdfImporter(): PdfImporter = DesktopPdfImporter()
