package com.Muncho.kaptal.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException

data class PdfCell(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float
)

data class PdfRow(
    val cells: List<PdfCell>
)

class PdfTableExtractor(private val context: Context) {

    fun extractTableData(uri: Uri, pageIndex: Int = 0): List<PdfRow> {
        val rows = mutableListOf<PdfRow>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val stripper = object : PDFTextStripper() {
                    private var currentY = -1f
                    private val tolerance = 3f // Tolerance pour grouper sur une même ligne
                    private val currentLineCells = mutableListOf<PdfCell>()

                    override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
                        if (string == null || textPositions == null || textPositions.isEmpty()) return

                        val firstChar = textPositions[0]
                        val y = firstChar.yDirAdj

                        if (currentY == -1f) {
                            currentY = y
                        }

                        // Si on change de ligne
                        if (Math.abs(y - currentY) > tolerance) {
                            if (currentLineCells.isNotEmpty()) {
                                rows.add(PdfRow(currentLineCells.toList().sortedBy { it.x }))
                                currentLineCells.clear()
                            }
                            currentY = y
                        }

                        // On ajoute le bloc de texte avec ses coordonnées
                        currentLineCells.add(PdfCell(
                            text = string.trim(),
                            x = firstChar.xDirAdj,
                            y = y,
                            width = textPositions.last().xDirAdj + textPositions.last().widthDirAdj - firstChar.xDirAdj
                        ))
                    }
                    
                    // On override pour s'assurer que la dernière ligne est ajoutée
                    override fun endDocument(document: PDDocument?) {
                        if (currentLineCells.isNotEmpty()) {
                            rows.add(PdfRow(currentLineCells.toList().sortedBy { it.x }))
                        }
                    }
                }

                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document)
                document.close()
            }
        } catch (e: Exception) {
            Log.e("PdfTableExtractor", "Error extracting PDF data", e)
        }

        return rows
    }
}
