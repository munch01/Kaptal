package com.muncho.kaptal.utils

import android.net.Uri
import com.muncho.kaptal.appContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPdfImporter : PdfImporter {
    override suspend fun extractTableData(uri: String): List<PdfRow> = withContext(Dispatchers.IO) {
        val allTextPositions = mutableListOf<PdfCell>()
        val androidUri = Uri.parse(uri)
        
        try {
            appContext.contentResolver.openInputStream(androidUri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val totalPages = document.numberOfPages
                
                for (p in 0 until totalPages) {
                    val stripper = object : PDFTextStripper() {
                        override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
                            if (string == null || textPositions == null || textPositions.isEmpty()) return
                            val firstChar = textPositions[0]
                            allTextPositions.add(PdfCell(
                                text = string.trim(),
                                x = firstChar.xDirAdj,
                                y = firstChar.yDirAdj,
                                width = textPositions.last().xDirAdj + textPositions.last().widthDirAdj - firstChar.xDirAdj,
                                page = p
                            ))
                        }
                    }
                    stripper.sortByPosition = true
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    stripper.getText(document)
                }
                document.close()
            }
        } catch (e: Exception) {
            return@withContext emptyList<PdfRow>()
        }

        if (allTextPositions.isEmpty()) return@withContext emptyList<PdfRow>()

        val rowsByY = allTextPositions.groupBy { cell ->
            (cell.page * 100000) + (cell.y / 3f).toInt() 
        }.toSortedMap()

        val xPositions = allTextPositions.map { it.x }.distinct().sorted()
        val columnStarts = mutableListOf<Float>()
        if (xPositions.isNotEmpty()) {
            columnStarts.add(xPositions[0])
            for (i in 1 until xPositions.size) {
                if (xPositions[i] - xPositions[i-1] > 12f) {
                    columnStarts.add(xPositions[i])
                }
            }
        }

        rowsByY.map { (_, cellsInRow) ->
            val alignedCells = mutableListOf<PdfCell>()
            val sortedInRow = cellsInRow.sortedBy { it.x }
            
            sortedInRow.forEach { cell ->
                val bestColIndex = columnStarts.indices.minByOrNull { Math.abs(columnStarts[it] - cell.x) } ?: 0
                cell.colIndex = bestColIndex
                alignedCells.add(cell)
            }
            PdfRow(alignedCells.sortedBy { it.colIndex })
        }
    }
}

actual fun getPdfImporter(): PdfImporter = AndroidPdfImporter()
