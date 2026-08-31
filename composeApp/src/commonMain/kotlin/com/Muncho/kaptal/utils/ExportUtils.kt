package com.muncho.kaptal.utils

import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.viewmodel.toInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ExportUtils {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun generateTransactionsCsv(account: Account, transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.append("Date;Titre;Montant;Type;Famille;Sous-Catégorie;Méthode de paiement\n")
        
        transactions.forEach { tx ->
            val dateStr = DateTimeUtils.formatDate(tx.date.toInstant(), "dd/MM/yyyy")
            sb.append("${dateStr};")
            sb.append("${tx.title?.replace(";", ",") ?: ""};")
            sb.append("${tx.amount.toString().replace(".", ",")};")
            sb.append("${tx.type};")
            sb.append("${tx.familyCategory?.replace(";", ",") ?: ""};")
            sb.append("${tx.subCategory?.replace(";", ",") ?: ""};")
            sb.append("${tx.paymentMethod}\n")
        }
        return sb.toString()
    }

    fun generateAccountJson(account: Account, transactions: List<Transaction>): String {
        // Wrapper class for serialization
        @kotlinx.serialization.Serializable
        data class AccountExport(
            val account: Account,
            val transactions: List<Transaction>
        )
        
        val export = AccountExport(account, transactions)
        return json.encodeToString(export)
    }
}
