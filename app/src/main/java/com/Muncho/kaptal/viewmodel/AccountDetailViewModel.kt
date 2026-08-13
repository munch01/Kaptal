package com.Muncho.kaptal.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Muncho.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import java.util.UUID

import java.util.Date
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
                    val otherAccountRef = db.collection("accounts")
                        .document(transaction.targetAccountId)
                        .collection("transactions")
                    
                    val otherTxSnapshot = otherAccountRef
                        .whereEqualTo("transferGroupId", transaction.transferGroupId)
                        .get()
                        .await()

                    for (doc in otherTxSnapshot.documents) {
                        if (doc.id != transaction.id) {
                            val otherTx = doc.toObject(Transaction::class.java)
                            if (otherTx != null) {
                                val updatedOtherTx = otherTx.copy(
                                    title = transaction.title,
                                    amount = -transaction.amount, // Inverser le montant
                                    date = transaction.date,
                                    isRecurring = transaction.isRecurring,
                                    recurrenceInterval = transaction.recurrenceInterval,
                                    endDate = transaction.endDate
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
        endDate: Timestamp?
    ) {
        viewModelScope.launch {
            try {
                val absAmount = abs(amount)
                val transferGroupId = UUID.randomUUID().toString()
                
                // 1. Transaction sortante (débit)
                val outTx = Transaction(
                    title = title,
                    amount = -absAmount,
                    type = "TRANSFER",
                    familyCategory = "Virement",
                    subCategory = "Virement interne",
                    date = date,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = targetAccountId
                )
                db.collection("accounts").document(sourceAccountId).collection("transactions").add(outTx).await()

                // 2. Transaction entrante (crédit)
                val inTx = Transaction(
                    title = title,
                    amount = absAmount,
                    type = "TRANSFER",
                    familyCategory = "Virement",
                    subCategory = "Virement interne",
                    date = date,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = sourceAccountId
                )
                db.collection("accounts").document(targetAccountId).collection("transactions").add(inTx).await()
            } catch (e: Exception) {
                Log.e("TRANSFER_DEBUG", "Erreur lors du virement", e)
            }
        }
    }

    fun generateInstallments(
        accountId: String,
        linkedAccountId: String?,
        amount: Double,
        months: Int,
        startDate: Date,
        title: String
    ) {
        viewModelScope.launch {
            try {
                val cal = Calendar.getInstance().apply { time = startDate }
                val transactionsRef = db.collection("accounts").document(accountId).collection("transactions")
                val linkedRef = linkedAccountId?.let { db.collection("accounts").document(it).collection("transactions") }

                for (i in 0 until months) {
                    val date = Timestamp(cal.time)
                    
                    // 1. Crédit (Réduction de la dette)
                    val creditTx = Transaction(
                        title = title,
                        amount = abs(amount),
                        type = "INCOME",
                        familyCategory = "Crédit",
                        subCategory = "Mensualité",
                        date = date,
                        checkedMonths = emptyList()
                    )
                    transactionsRef.add(creditTx)
                    
                    // 2. Débit (Sortie d'argent du compte lié)
                    if (linkedRef != null) {
                        val debitTx = Transaction(
                            title = title,
                            amount = -abs(amount),
                            type = "EXPENSE",
                            familyCategory = "Crédit",
                            subCategory = "Mensualité",
                            date = date,
                            checkedMonths = emptyList()
                        )
                        linkedRef.add(debitTx)
                    }
                    
                    cal.add(Calendar.MONTH, 1)
                }
            } catch (e: Exception) {
                Log.e("CREDIT_DEBUG", "Erreur génération mensualités", e)
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
        scope: RecurrenceEditScope
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
                        endDate = newEndDate
                    )
                    transactionsRef.document(oldTransaction.id).set(updated).await()
                    return@launch
                }

                val calEff = Calendar.getInstance().apply { time = effectiveDate.toDate() }

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
                            endDate = newEndDate
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
                                        endDate = newEndDate
                                    )
                                    otherAccountRef.document(doc.id).set(updatedOther).await()
                                }
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_AND_FUTURE -> {
                        transactionsRef.document(oldTransaction.id)
                            .update("endDate", effectiveDate)
                            .await()

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) UUID.randomUUID().toString() else null

                        val brandNewTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = effectiveDate,
                            isRecurring = newIsRecurring,
                            recurrenceInterval = newRecurrenceInterval,
                            endDate = newEndDate,
                            checkedMonths = emptyList(),
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId
                        )
                        transactionsRef.add(brandNewTx).await()

                        // Sync l'autre côté si c'est un virement
                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            // 1. Clore l'ancienne série de l'autre côté
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", oldTransaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            // 2. Créer la nouvelle série de l'autre côté
                            val otherNewTx = brandNewTx.copy(
                                amount = -newAmount, // Inverser
                                targetAccountId = accountId // Pointer vers le compte actuel
                            )
                            otherAccountRef.add(otherNewTx).await()
                        }
                    }

                    RecurrenceEditScope.THIS_ONLY -> {
                        // 1. La série d'origine s'arrête juste avant ce mois
                        transactionsRef.document(oldTransaction.id)
                            .update("endDate", effectiveDate)
                            .await()

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) UUID.randomUUID().toString() else null

                        // 2. Créer l'occurrence isolée modifiée pour ce mois précis (non récurrente)
                        val isolatedTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = effectiveDate,
                            isRecurring = false,
                            checkedMonths = emptyList(),
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId
                        )
                        transactionsRef.add(isolatedTx).await()

                        // 3. Reprendre la série initiale le mois suivant pour préserver le futur
                        val futureCal = (calEff.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                        val futureTimestamp = Timestamp(futureCal.time)

                        val remainingSeriesTx = oldTransaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = oldTransaction.endDate,
                            checkedMonths = emptyList()
                        )
                        transactionsRef.add(remainingSeriesTx).await()

                        // Sync l'autre côté si c'est un virement
                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            // 1. Clore l'ancienne série de l'autre côté
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", oldTransaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            // 2. Créer l'occurrence isolée de l'autre côté
                            val otherIsolatedTx = isolatedTx.copy(
                                amount = -newAmount,
                                targetAccountId = accountId
                            )
                            otherAccountRef.add(otherIsolatedTx).await()

                            // 3. Reprendre la série de l'autre côté
                            val otherRemainingSeriesTx = remainingSeriesTx.copy(
                                amount = -remainingSeriesTx.amount,
                                targetAccountId = accountId
                            )
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
                        transactionsRef.document(transaction.id)
                            .update("endDate", effectiveDate)
                            .await()
                        
                        // Sync l'autre côté si c'est un virement
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
                        transactionsRef.document(transaction.id)
                            .update("endDate", effectiveDate)
                            .await()

                        // 2. Reprendre la série le mois suivant pour préserver le futur sans ce mois-là
                        val futureCal = (calEff.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                        val futureTimestamp = Timestamp(futureCal.time)

                        val remainingSeriesTx = transaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = transaction.endDate,
                            checkedMonths = emptyList()
                        )
                        transactionsRef.add(remainingSeriesTx).await()

                        // Sync l'autre côté si c'est un virement
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            
                            // 1. Clore l'ancienne série de l'autre côté
                            val otherOldSnapshot = otherAccountRef.whereEqualTo("transferGroupId", transaction.transferGroupId).get().await()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate", effectiveDate).await()
                            }

                            // 2. Reprendre la série de l'autre côté
                            val otherRemainingSeriesTx = remainingSeriesTx.copy(
                                amount = -remainingSeriesTx.amount,
                                targetAccountId = accountId
                            )
                            otherAccountRef.add(otherRemainingSeriesTx).await()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DELETE_SCOPE_DEBUG", "Erreur lors de la suppression avec portée", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        currentAccountId = null
    }
}