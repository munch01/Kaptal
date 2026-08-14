package com.Muncho.kaptal.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.Muncho.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.*

data class LoanMetadata(
    val loanNumber: String?,
    val totalAmount: Double?,
    val durationMonths: Int?,
    val insuranceRate: Double? = null,
    val signingDate: String? = null,
    val monthlyPayment: Double? = null
)

data class AmortizationRow(
    val date: Date,
    val totalAmount: Double,
    val principal: Double,
    val interests: Double,
    val insurance: Double
)

object AmortizationParser {

    fun parseCaisseEpargne(context: Context, uri: Uri): Pair<LoanMetadata, List<AmortizationRow>> {
        val document = try {
            context.contentResolver.openInputStream(uri)?.use {
                PDDocument.load(it)
            }
        } catch (e: Exception) {
            Log.e("PDF_PARSER", "Erreur chargement document", e)
            null
        } ?: throw Exception("Impossible de lire le fichier PDF. Vérifiez qu'il s'agit bien d'un document valide.")

        val stripper = PDFTextStripper()
        val text = stripper.getText(document)
        document.close()

        Log.d("PDF_PARSER", "Texte extrait : ${text.take(500)}...")

        // 1. Extraire les métadonnées
        val loanNumber = Regex("""Numéro Prêt personnel\s*:\s*([\d\s]+)""").find(text)?.groupValues?.get(1)?.trim()
        val totalAmount = Regex("""Capital prêté\s*:\s*([\d\s,]+)""").find(text)?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull()
        val duration = Regex("""Durée\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val signingDate = Regex("""Le\s*(\d+[^0-9\n]+\d{4})""").find(text)?.groupValues?.get(1)?.trim()

        // 2. Extraire les lignes du tableau
        // Format typique : 001 | 04/09/2024 | 217,72 | 152,48 | 65,24
        val rows = mutableListOf<AmortizationRow>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        
        val lines = text.split("\n")
        for (line in lines) {
            // Motif pour une ligne de tableau : N°(3 chiffres) Date(DD/MM/YYYY) Montant(XXX,XX)
            val match = Regex("""(\d{3})\s+(\d{2}/\d{2}/\d{4})\s+([\d\s,.]+)\s+([\d\s,.]+)\s+([\d\s,.]+)""").find(line)
            if (match != null) {
                try {
                    val date = dateFormat.parse(match.groupValues[2]) ?: continue
                    val total = match.groupValues[3].cleanAmount()
                    val principal = match.groupValues[4].cleanAmount()
                    val interests = match.groupValues[5].cleanAmount()
                    
                    // Calcul intelligent de l'assurance pour éviter de confondre avec le capital restant
                    val diff = total - (principal + interests)
                    val insurance = if (diff > 0.01) {
                        // Si il y a un écart, on regarde si le nombre suivant correspond
                        val remainingText = line.substring(match.range.last + 1).trim()
                        val nextValueMatch = Regex("""^([\d\s,.]+)""").find(remainingText)
                        val nextValue = nextValueMatch?.groupValues?.get(1)?.cleanAmount() ?: 0.0
                        
                        if (Math.abs(nextValue - diff) < 0.05) nextValue else 0.0
                    } else 0.0

                    rows.add(AmortizationRow(date, total, principal, interests, insurance))
                } catch (e: Exception) {
                    // Ignorer les lignes mal formées
                }
            }
        }

        return LoanMetadata(
            loanNumber = loanNumber,
            totalAmount = totalAmount,
            durationMonths = duration,
            signingDate = signingDate,
            monthlyPayment = rows.firstOrNull()?.totalAmount
        ) to rows
    }

    private fun String.cleanAmount(): Double {
        return this.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    }
}
