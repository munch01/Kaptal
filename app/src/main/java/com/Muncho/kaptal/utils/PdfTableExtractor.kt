package com.muncho.kaptal.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.util.*

data class PdfCell(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    var colIndex: Int = -1,
    val page: Int = 0
)

data class PdfRow(
    val cells: List<PdfCell>
)

class PdfTableExtractor(private val context: Context) {

    fun extractTableData(uri: Uri): List<PdfRow> {
        val allTextPositions = mutableListOf<PdfCell>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
            Log.e("PdfTableExtractor", "Error extracting PDF data", e)
            return emptyList()
        }

        if (allTextPositions.isEmpty()) return emptyList()

        // 1. Grouper par lignes (Page + Y similaires)
        // On multiplie la page par une grande valeur pour que les lignes se suivent chronologiquement
        val rowsByY = allTextPositions.groupBy { cell ->
            (cell.page * 100000) + (cell.y / 3f).toInt() 
        }.toSortedMap()

        // 2. Déterminer les colonnes virtuelles (Basé sur tout le document pour plus de robustesse)
        val xPositions = allTextPositions.map { it.x }.distinct().sorted()
        val columnStarts = mutableListOf<Float>()
        if (xPositions.isNotEmpty()) {
            columnStarts.add(xPositions[0])
            for (i in 1 until xPositions.size) {
                if (xPositions[i] - xPositions[i-1] > 12f) { // Seuil légèrement réduit pour plus de précision
                    columnStarts.add(xPositions[i])
                }
            }
        }

        // 3. Reconstruire les lignes alignées
        val finalRows = rowsByY.map { (_, cellsInRow) ->
            val alignedCells = mutableListOf<PdfCell>()
            val sortedInRow = cellsInRow.sortedBy { it.x }
            
            sortedInRow.forEach { cell ->
                val bestColIndex = columnStarts.indices.minByOrNull { Math.abs(columnStarts[it] - cell.x) } ?: 0
                cell.colIndex = bestColIndex
                alignedCells.add(cell)
            }
            PdfRow(alignedCells.sortedBy { it.colIndex })
        }

        return finalRows
    }
}
