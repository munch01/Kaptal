package com.muncho.kaptal.viewmodel

import com.muncho.kaptal.utils.PdfRow
import com.muncho.kaptal.utils.PdfTableExtractor
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class RecurrenceEditScope {
    ALL,
    THIS_AND_FUTURE,
    THIS_ONLY
}

class AccountDetailViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var currentAccountId: String? = null

    fun loadTransactions(accountId: String) {
        if (accountId.isEmpty()) return

        if (currentAccountId == accountId && listenerRegistration != null) return

        currentAccountId = accountId
        listenerRegistration?.remove()

        listenerRegistration = db.collection("accounts")
            .document(accountId)
            .collection("transactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FIRESTORE_DEBUG", "Erreur écoute transactions", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                    }
                    _transactions.value = list
                }
            }
    }

    fun toggleTransactionCheck(
        accountId: String,
        transactionId: String,
        monthKey: String,
        newCheckedStatus: Boolean
    ) {
        if (accountId.isEmpty() || transactionId.isEmpty() || monthKey.isEmpty()) return

        val currentTx = _transactions.value.find { it.id == transactionId } ?: return

        val updatedMonths = if (newCheckedStatus) {
            if (!currentTx.checkedMonths.contains(monthKey)) currentTx.checkedMonths + monthKey else currentTx.checkedMonths
        } else {
            currentTx.checkedMonths - monthKey
        }

        _transactions.value = _transactions.value.map { tx ->
            if (tx.id == transactionId) tx.copy(checkedMonths = updatedMonths) else tx
        }

        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transactionId)
                    .update("checkedMonths", updatedMonths)
                    .await()
            } catch (e: Exception) {
                Log.e("CHECK_DEBUG", "Erreur Firestore lors du pointage mensuel", e)
                _transactions.value = _transactions.value.map { tx ->
                    if (tx.id == transactionId) currentTx else tx
                }
            }
        }
    }

    fun addTransaction(accountId: String, transaction: Transaction) {
        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .add(transaction)
                    .await()
            } catch (e: Exception) {
                Log.e("ADD_DEBUG", "Erreur ajout transaction", e)
            }
        }
    }

    fun updateTransaction(accountId: String, transaction: Transaction) {
        viewModelScope.launch {
            try {
                // 1. Mise à jour de la transaction actuelle
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transaction.id)
                    .set(transaction)
                    .await()

                // 2. Si c'est un virement, mettre à jour l'autre côté
                if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                    val otherAccountId = transaction.targetAccountId
                    val otherAccountRef = db.collection("accounts")
                        .document(otherAccountId)
                        .collection("transactions")
                    
                    val otherAccountDoc = db.collection("accounts").document(otherAccountId).get().await()
                    val otherIsCrypto = otherAccountDoc.getString("type") == "CRYPTO"
                    val currentAccountDoc = db.collection("accounts").document(accountId).get().await()
                    val currentIsCrypto = currentAccountDoc.getString("type") == "CRYPTO"

                    val otherTxSnapshot = otherAccountRef
                        .whereEqualTo("transferGroupId", transaction.transferGroupId)
                        .get()
                        .await()

                    for (doc in otherTxSnapshot.documents) {
                        if (doc.id != transaction.id) {
                            val otherTx = doc.toObject(Transaction::class.java)
                            if (otherTx != null) {
                                // Calcul du nouveau montant pour l'autre côté
                                val newOtherAmount = if (currentIsCrypto && !otherIsCrypto) {
                                    // On vient de la crypto vers le FIAT -> Utiliser investmentEur
                                    abs(transaction.investmentEur ?: transaction.amount)
                                } else if (!currentIsCrypto && otherIsCrypto) {
                                    // On vient du FIAT vers la crypto -> Garder l'ancienne quantité crypto ou recalculer ?
                                    // Pour éviter le chiffre aléatoire, on garde l'ancienne quantité si l'unité change
                                    otherTx.amount 
                                } else {
                                    // Même type (EUR -> EUR) -> Inversion simple
                                    -transaction.amount
                                }

                                val updatedOtherTx = otherTx.copy(
                                    title = transaction.title,
                                    amount = newOtherAmount,
                                    date = transaction.date,
                                    isRecurring = transaction.isRecurring,
                                    recurrenceInterval = transaction.recurrenceInterval,
                                    endDate = transaction.endDate,
                                    investmentEur = transaction.investmentEur,
                                    feesPercent = transaction.feesPercent
                                )
                                otherAccountRef.document(doc.id).set(updatedOtherTx).await()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UPDATE_DEBUG", "Erreur modification transaction", e)
            }
        }
    }

    fun performTransfer(
        sourceAccountId: String,
        targetAccountId: String,
        amount: Double,
        title: String,
        date: Timestamp,
        isRecurring: Boolean,
        recurrenceInterval: String?,
        endDate: Timestamp?,
        investmentEur: Double? = null,
        feesPercent: Double? = null,
        cryptoRate: Double? = null // Nouveau paramètre
    ) {
        viewModelScope.launch {
            try {
                val sourceAccDoc = db.collection("accounts").document(sourceAccountId).get().await()
                val targetAccDoc = db.collection("accounts").document(targetAccountId).get().await()
                
                val sourceIsCrypto = sourceAccDoc.getString("type") == "CRYPTO"
                val targetIsCrypto = targetAccDoc.getString("type") == "CRYPTO"
                
                val absSaisi = abs(amount)
                val transferGroupId = UUID.randomUUID().toString()
                
                val rate = cryptoRate ?: 1.0

                // Détermination des montants pour chaque côté
                val (sourceVal, targetVal, fiatVal) = when {
                    sourceIsCrypto && !targetIsCrypto -> {
                        // Crypto -> FIAT (Vente)
                        val btcQty = absSaisi
                        val eurVal = investmentEur ?: (btcQty * rate)
                        Triple(btcQty, eurVal, eurVal)
                    }
                    !sourceIsCrypto && targetIsCrypto -> {
                        // FIAT -> Crypto (Achat)
                        val eurVal = absSaisi
                        val btcQty = investmentEur?.let { if (rate > 0) it / rate else 0.0 } ?: (eurVal / rate)
                        Triple(eurVal, btcQty, eurVal)
                    }
                    else -> {
                        // Même type (EUR -> EUR)
                        Triple(absSaisi, absSaisi, investmentEur ?: absSaisi)
                    }
                }

                // 1. Transaction sortante (débit)
                val outTx = Transaction(
                    title = title,
                    amount = -sourceVal,
                    type = "TRANSFER",
                    familyCategory = "Virement",
                    subCategory = "Virement interne",
                    date = date,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = targetAccountId,
                    investmentEur = if (sourceIsCrypto) fiatVal else null
                )
                db.collection("accounts").document(sourceAccountId).collection("transactions").add(outTx).await()

                // 2. Transaction entrante (crédit)
                val inTx = Transaction(
                    title = title,
                    amount = targetVal,
                    type = "TRANSFER",
                    familyCategory = "Virement",
                    subCategory = "Virement interne",
                    date = date,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = sourceAccountId,
                    investmentEur = if (targetIsCrypto) fiatVal else null,
                    feesPercent = if (targetIsCrypto) feesPercent else null
                )
                db.collection("accounts").document(targetAccountId).collection("transactions").add(inTx).await()
            } catch (e: Exception) {
                Log.e("TRANSFER_DEBUG", "Erreur lors du virement", e)
            }
        }
    }

    /**
     * Gestion avancée de la modification d'une récurrence selon la portée choisie.
     */
    fun updateRecurringTransactionWithScope(
        accountId: String,
        oldTransaction: Transaction,
        newTitle: String,
        newAmount: Double,
        newFamilyCategory: String,
        newSubCategory: String,
        newType: String,
        newPaymentMethod: String,
        newDate: Timestamp,
        newIsRecurring: Boolean,
        newRecurrenceInterval: String,
        newEndDate: Timestamp?,
        effectiveDate: Timestamp,
        scope: RecurrenceEditScope,
        investmentEur: Double? = null,
        feesPercent: Double? = null
    ) {
        if (accountId.isEmpty() || oldTransaction.id.isEmpty()) return

        viewModelScope.launch {
            try {
                val transactionsRef = db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")

                if (!oldTransaction.isRecurring) {
                    val updated = oldTransaction.copy(
                        title = newTitle,
                        amount = newAmount,
                        familyCategory = newFamilyCategory,
                        subCategory = newSubCategory,
                        type = newType,
                        paymentMethod = newPaymentMethod,
                        date = newDate,
                        isRecurring = newIsRecurring,
                        recurrenceInterval = newRecurrenceInterval,
                        endDate = newEndDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent
                    )
                    transactionsRef.document(oldTransaction.id).set(updated).await()
                    return@launch
                }

                val calEff = Calendar.getInstance().apply { time = effectiveDate.toDate() }
                val currentMonthIndex = calEff.get(Calendar.YEAR) * 12 + calEff.get(Calendar.MONTH)

                when (scope) {
                    RecurrenceEditScope.ALL -> {
                        val updated = oldTransaction.copy(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = newDate,
                            isRecurring = newIsRecurring,
                            recurrenceInterval = newRecurrenceInterval,
                            endDate = newEndDate,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                            // checkedMonths est préservé par copy()
                        )
                        transactionsRef.document(oldTransaction.id).set(updated).await()

                        // Sync l'autre côté si c'est un virement
                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.whereEqualTo("transferGroupId", oldTransaction.transferGroupId).get().await()
                            for (doc in otherTxSnapshot.documents) {
                                val otherTx = doc.toObject(Transaction::class.java)
                                if (otherTx != null) {
                                    val updatedOther = otherTx.copy(
                                        title = newTitle,
                                        amount = -newAmount,
                                        date = newDate,
                                        isRecurring = newIsRecurring,
                                        recurrenceInterval = newRecurrenceInterval,
                                        endDate = newEndDate,
                                        investmentEur = investmentEur,
                                        feesPercent = feesPercent
                                    )
                                    otherAccountRef.document(doc.id).set(updatedOther).await()
                                }
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_AND_FUTURE -> {
                        // 1. On sépare les pointages
                        val oldPointages = mutableListOf<String>()
                        val newPointages = mutableListOf<String>()
                        
                        oldTransaction.checkedMonths.forEach { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                if (y * 12 + m < currentMonthIndex) oldPointages.add(mKey)
                                else newPointages.add(mKey)
                            } catch (e: Exception) { oldPointages.add(mKey) }
                        }

                        // 2. Mettre à jour l'ancienne série
                        transactionsRef.document(oldTransaction.id)
                            .update("endDate", effectiveDate, "checkedMonths", oldPointages)
                            .await()

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) UUID.randomUUID().toString() else null

                        // 3. Créer la nouvelle série
                        val brandNewTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = newDate,
                            isRecurring = newIsRecurring,
                            recurrenceInterval = newRecurrenceInterval,
                            endDate = newEndDate,
                            checkedMonths = newPointages,
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        transactionsRef.add(brandNewTx).await()

                        // Sync l'autre côté si c'est un virement
                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", oldTransaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            val otherNewTx = brandNewTx.copy(
                                amount = -newAmount,
                                targetAccountId = accountId,
                                checkedMonths = emptyList() // On ne copie pas les pointages sur l'autre compte car c'est géré par l'utilisateur
                            )
                            otherAccountRef.add(otherNewTx).await()
                        }
                    }

                    RecurrenceEditScope.THIS_ONLY -> {
                        val currentMonthKey = String.format(Locale.US, "%d-%02d", calEff.get(Calendar.YEAR), calEff.get(Calendar.MONTH) + 1)
                        val isThisMonthChecked = oldTransaction.isCheckedForMonth(currentMonthKey)
                        
                        // 1. La série d'origine s'arrête juste avant ce mois
                        val oldPointages = oldTransaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }
                        
                        // 3. Pointages pour la reprise du futur
                        val futurePointages = oldTransaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m > currentMonthIndex
                            } catch (e: Exception) { false }
                        }

                        transactionsRef.document(oldTransaction.id)
                            .update("endDate", effectiveDate, "checkedMonths", oldPointages)
                            .await()

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) UUID.randomUUID().toString() else null

                        // 2. Créer l'occurrence isolée
                        val isolatedTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = newDate, 
                            isRecurring = false,
                            checkedMonths = if (isThisMonthChecked) listOf(currentMonthKey) else emptyList(),
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        transactionsRef.add(isolatedTx).await()

                        // 3. Reprendre la série initiale le mois suivant
                        val futureCal = (calEff.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                        val futureTimestamp = Timestamp(futureCal.time)

                        val remainingSeriesTx = oldTransaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = oldTransaction.endDate,
                            checkedMonths = futurePointages
                        )
                        transactionsRef.add(remainingSeriesTx).await()

                        // Sync l'autre côté...
                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", oldTransaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            val otherIsolatedTx = isolatedTx.copy(amount = -newAmount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherIsolatedTx).await()

                            val otherRemainingSeriesTx = remainingSeriesTx.copy(amount = -remainingSeriesTx.amount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherRemainingSeriesTx).await()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UPDATE_SCOPE_DEBUG", "Erreur lors de la modification avec portée", e)
            }
        }
    }

    /**
     * Suppression simple (rétrocompatibilité)
     */
    fun deleteTransaction(
        accountId: String,
        transaction: Transaction,
        effectiveDeleteDate: Timestamp? = null
    ) {
        deleteRecurringTransactionWithScope(
            accountId = accountId,
            transaction = transaction,
            effectiveDate = effectiveDeleteDate ?: transaction.date,
            scope = RecurrenceEditScope.THIS_AND_FUTURE
        )
    }

    /**
     * Gestion avancée de la suppression d'une récurrence selon la portée choisie.
     */
    fun deleteRecurringTransactionWithScope(
        accountId: String,
        transaction: Transaction,
        effectiveDate: Timestamp,
        scope: RecurrenceEditScope
    ) {
        if (accountId.isEmpty() || transaction.id.isEmpty()) return

        viewModelScope.launch {
            try {
                val transactionsRef = db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")

                if (!transaction.isRecurring) {
                    transactionsRef.document(transaction.id).delete().await()
                    return@launch
                }

                val calEff = Calendar.getInstance().apply { time = effectiveDate.toDate() }
                val currentMonthIndex = calEff.get(Calendar.YEAR) * 12 + calEff.get(Calendar.MONTH)

                when (scope) {
                    RecurrenceEditScope.ALL -> {
                        transactionsRef.document(transaction.id).delete().await()
                        
                        // Supprimer l'autre côté si c'est un virement
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.whereEqualTo("transferGroupId", transaction.transferGroupId).get().await()
                            for (doc in otherTxSnapshot.documents) {
                                otherAccountRef.document(doc.id).delete().await()
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_AND_FUTURE -> {
                        // On garde les pointages passés uniquement
                        val oldPointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }

                        transactionsRef.document(transaction.id)
                            .update("endDate", effectiveDate, "checkedMonths", oldPointages)
                            .await()
                        
                        // Sync l'autre côté
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.whereEqualTo("transferGroupId", transaction.transferGroupId).get().await()
                            for (doc in otherTxSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_ONLY -> {
                        // 1. Arrêter la série initiale juste avant ce mois
                        val oldPointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }

                        // Pointages pour le futur (après ce mois)
                        val futurePointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m > currentMonthIndex
                            } catch (e: Exception) { false }
                        }

                        transactionsRef.document(transaction.id)
                            .update("endDate", effectiveDate, "checkedMonths", oldPointages)
                            .await()

                        // 2. Reprendre la série le mois suivant
                        val futureCal = (calEff.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                        val futureTimestamp = Timestamp(futureCal.time)

                        val remainingSeriesTx = transaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = transaction.endDate,
                            checkedMonths = futurePointages
                        )
                        transactionsRef.add(remainingSeriesTx).await()

                        // Sync l'autre côté...
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", transaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            val otherRemainingSeriesTx = remainingSeriesTx.copy(amount = -remainingSeriesTx.amount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherRemainingSeriesTx).await()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DELETE_SCOPE_DEBUG", "Erreur lors de la suppression avec portée", e)
            }
        }
    }

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    fun clearImportStatus() { _importStatus.value = null }

    private suspend fun clearExistingLoanData(accountId: String, linkedAccountId: String?) {
        // 1. Supprimer transactions du compte crédit
        val selfTxs = db.collection("accounts").document(accountId).collection("transactions").get().await()
        val batch = db.batch()
        selfTxs.forEach { batch.delete(it.reference) }
        
        // 2. Supprimer les débits liés sur le compte courant
        if (linkedAccountId != null) {
            val linkedTxs = db.collection("accounts").document(linkedAccountId).collection("transactions")
                .whereEqualTo("targetAccountId", accountId)
                .get().await()
            linkedTxs.forEach { batch.delete(it.reference) }
        }
        batch.commit().await()
    }

    fun generateLoanInstallments(
        context: Context,
        account: Account,
        startDate: Date,
        monthlyPayment: Double,
        durationMonths: Int,
        totalCapital: Double,
        insurance: Double,
        rate: Double,
        withdrawalDay: Int
    ) {
        viewModelScope.launch {
            try {
                _importStatus.value = "Nettoyage des anciennes données..."
                clearExistingLoanData(account.id, account.linkedAccountId)
                
                _importStatus.value = "Génération de l'échéancier..."
                
                // 1. Mettre à jour l'en-tête du compte
                val updates = mutableMapOf<String, Any?>(
                    "totalAmount" to totalCapital,
                    "loanStartDate" to SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(startDate),
                    "loanMonthlyPayment" to monthlyPayment,
                    "loanInsurance" to insurance,
                    "loanRate" to rate,
                    "loanTotalRepayment" to (monthlyPayment + insurance) * durationMonths
                )
                
                // Calcul de la date de fin
                val calEnd = Calendar.getInstance().apply { 
                    time = startDate
                    add(Calendar.MONTH, durationMonths - 1) 
                }
                updates["loanEndDate"] = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(calEnd.time)

                db.collection("accounts").document(account.id).update(updates).await()

                // 2. Préparer les dates
                val cal = Calendar.getInstance().apply { time = startDate }
                val currentMonthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val transactionsRef = db.collection("accounts").document(account.id).collection("transactions")
                val batch = db.batch()
                val totalMonthly = monthlyPayment + insurance
                val transferGroupIdPrefix = "LOAN_${account.id}_"
                
                var remainingCapital = totalCapital
                val monthlyRate = (rate / 100.0) / 12.0

                for (i in 0 until durationMonths) {
                    val interestPart = if (monthlyRate > 0) remainingCapital * monthlyRate else 0.0
                    val principalRepaid = monthlyPayment - interestPart
                    
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(withdrawalDay, maxDay))
                    
                    val date = Timestamp(cal.time)
                    val currentGroupId = "${transferGroupIdPrefix}${i}"
                    
                    val creditTx = Transaction(
                        title = account.name,
                        amount = totalMonthly,
                        type = "INCOME",
                        familyCategory = "Crédit",
                        subCategory = "Amortissement",
                        date = date,
                        checkedMonths = emptyList(),
                        principalPart = principalRepaid,
                        interestPart = interestPart,
                        insurancePart = insurance,
                        remainingDebt = remainingCapital - principalRepaid,
                        transferGroupId = currentGroupId,
                        targetAccountId = account.linkedAccountId
                    )
                    batch.set(transactionsRef.document(), creditTx)

                    remainingCapital -= principalRepaid
                    if (remainingCapital < 0) remainingCapital = 0.0

                    if (account.linkedAccountId != null && !cal.before(currentMonthStart)) {
                        val debitTx = Transaction(
                            title = account.name,
                            amount = -totalMonthly,
                            type = "EXPENSE",
                            familyCategory = "Crédit",
                            subCategory = "Mensualité",
                            date = date,
                            checkedMonths = emptyList(),
                            transferGroupId = currentGroupId,
                            targetAccountId = account.id
                        )
                        batch.set(db.collection("accounts").document(account.linkedAccountId).collection("transactions").document(), debitTx)
                    }
                    cal.add(Calendar.MONTH, 1)
                }
                
                batch.commit().await()
                _importStatus.value = "Échéancier généré : $durationMonths mensualités créées."
                
            } catch (e: Exception) {
                Log.e("LOAN_GEN_DEBUG", "Erreur lors de la génération", e)
                _importStatus.value = "Erreur lors de la génération : ${e.localizedMessage}"
            }
        }
    }

    private val _pdfRows = MutableStateFlow<List<PdfRow>>(emptyList())
    val pdfRows: StateFlow<List<PdfRow>> = _pdfRows.asStateFlow()

    fun extractPdfData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _importStatus.value = "Analyse spatiale du document..."
                val extractor = PdfTableExtractor(context)
                val rows = withContext(Dispatchers.IO) { extractor.extractTableData(uri) }
                _pdfRows.value = rows
                _importStatus.value = if (rows.isNotEmpty()) "Prêt à identifier les colonnes." else "Erreur : PDF vide"
            } catch (e: Exception) {
                _importStatus.value = "Erreur lecture PDF : ${e.localizedMessage}"
            }
        }
    }

    fun clearPdfData() { _pdfRows.value = emptyList() }

    fun importFromSelectedColumns(
        accountId: String,
        linkedAccountId: String?,
        startRowIdx: Int,
        dateColIdx: Int,
        amountColIdx: Int,
        principalColIdx: Int,
        interestColIdx: Int,
        insuranceColIdx: Int,
        remainingDebtColIdx: Int
    ) {
        val rows = _pdfRows.value
        if (rows.isEmpty()) return

        viewModelScope.launch {
            try {
                _importStatus.value = "Nettoyage des anciennes données..."
                clearExistingLoanData(accountId, linkedAccountId)

                _importStatus.value = "Importation des lignes..."
                val dateFormatPatterns = listOf(
                    SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE),
                    SimpleDateFormat("dd/MM/yy", Locale.FRANCE),
                    SimpleDateFormat("d/MM/yyyy", Locale.FRANCE),
                    SimpleDateFormat("d/MM/yy", Locale.FRANCE)
                )
                
                val currentMonthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val batch = db.batch()
                val transactionsRef = db.collection("accounts").document(accountId).collection("transactions")
                val transferGroupIdPrefix = "LOAN_PDF_${accountId}_"
                
                // Récupération du nom du compte UNE SEULE FOIS pour les libellés
                val accountName = db.collection("accounts").document(accountId).get().await().getString("name") ?: "Prêt"
                
                var importedCount = 0
                var firstDate: Date? = null
                var lastDate: Date? = null
                val amountValues = mutableListOf<Double>()

                rows.drop(startRowIdx).forEachIndexed { index, pdfRow ->
                    val dateCell = pdfRow.cells.find { it.colIndex == dateColIdx }
                    val amountCell = pdfRow.cells.find { it.colIndex == amountColIdx }
                    val principalCell = if (principalColIdx != -1) pdfRow.cells.find { it.colIndex == principalColIdx } else null
                    val interestCell = if (interestColIdx != -1) pdfRow.cells.find { it.colIndex == interestColIdx } else null
                    val insuranceCell = if (insuranceColIdx != -1) pdfRow.cells.find { it.colIndex == insuranceColIdx } else null
                    val debtCell = if (remainingDebtColIdx != -1) pdfRow.cells.find { it.colIndex == remainingDebtColIdx } else null

                    var date: Date? = null
                    val rawDateText = dateCell?.text?.trim() ?: ""
                    for (pattern in dateFormatPatterns) {
                        try {
                            date = pattern.parse(rawDateText)
                            if (date != null) break
                        } catch (e: Exception) {}
                    }
                    
                    val amount = amountCell?.text?.cleanAmount() ?: 0.0

                    if (date != null && amount > 0) {
                        amountValues.add(amount)
                        val timestamp = Timestamp(date)
                        if (firstDate == null) firstDate = date
                        lastDate = date

                        val currentGroupId = "${transferGroupIdPrefix}${index}"
                        
                        val creditTx = Transaction(
                            title = accountName,
                            amount = amount,
                            type = "INCOME",
                            familyCategory = "Crédit",
                            subCategory = "Amortissement",
                            date = timestamp,
                            checkedMonths = emptyList(),
                            principalPart = principalCell?.text?.cleanAmount(),
                            interestPart = interestCell?.text?.cleanAmount(),
                            insurancePart = insuranceCell?.text?.cleanAmount(),
                            remainingDebt = debtCell?.text?.cleanAmount(),
                            transferGroupId = currentGroupId,
                            targetAccountId = linkedAccountId
                        )
                        batch.set(transactionsRef.document(), creditTx)

                        if (linkedAccountId != null && !date.before(currentMonthStart.time)) {
                            val debitTx = Transaction(
                                title = accountName,
                                amount = -amount,
                                type = "EXPENSE",
                                familyCategory = "Crédit",
                                subCategory = "Mensualité",
                                date = timestamp,
                                checkedMonths = emptyList(),
                                transferGroupId = currentGroupId,
                                targetAccountId = accountId
                            )
                            batch.set(db.collection("accounts").document(linkedAccountId).collection("transactions").document(), debitTx)
                        }
                        importedCount++
                    }
                }

                if (importedCount > 0) {
                    val updates = mutableMapOf<String, Any?>()
                    val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                    if (firstDate != null) updates["loanStartDate"] = outputFormat.format(firstDate!!)
                    if (lastDate != null) updates["loanEndDate"] = outputFormat.format(lastDate!!)
                    
                    // Somme réelle de toutes les mensualités lues
                    val totalRepayment = amountValues.sum()
                    val commonAmount = amountValues.groupBy { it }.maxByOrNull { it.value.size }?.key
                    
                    if (commonAmount != null) {
                        updates["loanMonthlyPayment"] = commonAmount
                    }
                    updates["loanTotalRepayment"] = totalRepayment
                    
                    db.collection("accounts").document(accountId).update(updates).await()
                    batch.commit().await()
                    _importStatus.value = "Importation réussie : $importedCount mensualités."
                }
else {
                    _importStatus.value = "Erreur : Aucune donnée valide trouvée."
                }
                clearPdfData()
            } catch (e: Exception) {
                _importStatus.value = "Erreur import : ${e.localizedMessage}"
            }
        }
    }

    fun updateLoanMetadata(accountId: String, totalCapital: Double, accountName: String) {
        viewModelScope.launch {
            try {
                // 1. Récupérer l'ancien montant pour calculer le décalage
                val accountRef = db.collection("accounts").document(accountId)
                val oldAmount = accountRef.get().await().getDouble("totalAmount") ?: 0.0
                val diff = totalCapital - oldAmount

                // 2. Mettre à jour le capital emprunté
                accountRef.update("totalAmount", totalCapital).await()
                
                // 3. MIGRATION : Nettoyer les libellés et ajuster le capital restant sur TOUTES les transactions
                val selfTxs = accountRef.collection("transactions").get().await()
                val batch = db.batch()
                
                selfTxs.forEach { doc ->
                    val updates = mutableMapOf<String, Any?>()
                    updates["title"] = accountName
                    
                    // Si on a un capital restant (venant du PDF), on lui applique le décalage
                    val currentRemaining = doc.getDouble("remainingDebt")
                    if (currentRemaining != null) {
                        updates["remainingDebt"] = currentRemaining + diff
                    }
                    
                    batch.update(doc.reference, updates)
                }
                
                // Sur le compte de prélèvement (si lié)
                val linkedId = accountRef.get().await().getString("linkedAccountId")
                if (!linkedId.isNullOrBlank()) {
                    val linkedAccountRef = db.collection("accounts").document(linkedId)
                    
                    // On scanne TOUTES les transactions du compte lié (plus sûr)
                    val allLinkedTxs = linkedAccountRef.collection("transactions").get().await()
                        
                    allLinkedTxs.forEach { doc ->
                        val title = doc.getString("title") ?: ""
                        val targetId = doc.getString("targetAccountId")
                        
                        // Détection très large des anciens libellés techniques
                        if (targetId == accountId || 
                            title.contains("Prélèvement", ignoreCase = true) || 
                            title.contains("Import", ignoreCase = true) ||
                            title.contains("Mensualité", ignoreCase = true) ||
                            title.contains("Echéance", ignoreCase = true) ||
                            title.contains("pret", ignoreCase = true) ||
                            title.contains("pdf", ignoreCase = true)) {
                            batch.update(doc.reference, "title", accountName)
                        }
                    }
                }
                
                batch.commit().await()
                _importStatus.value = "Capital et données synchronisés."
            } catch (e: Exception) {
                _importStatus.value = "Erreur mise à jour : ${e.localizedMessage}"
            }
        }
    }

    private fun String.cleanAmount(): Double {
        val cleaned = this.replace(Regex("[^0-9,.-]"), "").replace(",", ".")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        currentAccountId = null
    }
}