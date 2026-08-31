package com.muncho.kaptal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.utils.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.math.abs

enum class RecurrenceEditScope {
    ALL,
    THIS_AND_FUTURE,
    THIS_ONLY
}

class AccountDetailViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private var currentAccountId: String? = null
    private var listenerJob: kotlinx.coroutines.Job? = null

    fun clearImportStatus() { _importStatus.value = null }

    fun loadTransactions(accountId: String) {
        if (accountId.isBlank()) return
        if (currentAccountId == accountId && listenerJob != null) return

        currentAccountId = accountId
        listenerJob?.cancel()

        listenerJob = viewModelScope.launch {
            db.collection("accounts")
                .document(accountId)
                .collection("transactions")
                .orderBy("date", Direction.DESCENDING)
                .snapshots().collect { snapshot ->
                    val list = snapshot.documents.map { doc ->
                        doc.data<Transaction>().copy(id = doc.id)
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
        val currentTx = _transactions.value.find { it.id == transactionId } ?: return
        val updatedMonths = if (newCheckedStatus) {
            if (!currentTx.checkedMonths.contains(monthKey)) currentTx.checkedMonths + monthKey else currentTx.checkedMonths
        } else {
            currentTx.checkedMonths - monthKey
        }

        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transactionId)
                    .update("checkedMonths" to updatedMonths)
            } catch (e: Exception) {
                getPlatform().log("CHECK_ERROR", e.message ?: "")
            }
        }
    }

    fun addTransaction(accountId: String, transaction: Transaction) {
        val safeTx = transaction.copy(date = DateTimeUtils.toSafeInstant(transaction.date.toInstant()).toTimestamp())
        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .add(safeTx)
            } catch (e: Exception) { }
        }
    }

    fun deleteTransaction(accountId: String, transactionId: String) {
        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transactionId)
                    .delete()
            } catch (e: Exception) { }
        }
    }

    fun updateTransaction(accountId: String, transaction: Transaction) {
        val safeTx = transaction.copy(date = DateTimeUtils.toSafeInstant(transaction.date.toInstant()).toTimestamp())
        viewModelScope.launch {
            try {
                getPlatform().log("TX_EDIT", "Mise à jour transaction ${safeTx.id} dans compte $accountId")
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(safeTx.id)
                    .set(safeTx)
                getPlatform().log("TX_EDIT", "Succès mise à jour transaction principale")

                if (!safeTx.transferGroupId.isNullOrBlank() && !safeTx.targetAccountId.isNullOrBlank()) {
                    getPlatform().log("TX_EDIT", "Mise à jour du transfert lié (groupe ${safeTx.transferGroupId})")
                    val otherAccountId = safeTx.targetAccountId
                    val otherAccountRef = db.collection("accounts")
                        .document(otherAccountId)
                        .collection("transactions")
                    
                    val otherAccountDoc = db.collection("accounts").document(otherAccountId).get()
                    val otherIsCrypto = try { otherAccountDoc.get<String?>("type") == "CRYPTO" } catch (e: Exception) { false }
                    val currentAccountDoc = db.collection("accounts").document(accountId).get()
                    val currentIsCrypto = try { currentAccountDoc.get<String?>("type") == "CRYPTO" } catch (e: Exception) { false }

                    val otherTxSnapshot = otherAccountRef
                        .where { "transferGroupId" equalTo safeTx.transferGroupId }
                        .get()

                    for (doc in otherTxSnapshot.documents) {
                        if (doc.id != safeTx.id) {
                            val otherTx = doc.data<Transaction>().copy(id = doc.id)
                            getPlatform().log("TX_EDIT", "Mise à jour transaction miroir ${doc.id} sur compte $otherAccountId")
                            
                            val newOtherAmount = if (currentIsCrypto && !otherIsCrypto) {
                                abs(safeTx.investmentEur ?: safeTx.amount)
                            } else if (!currentIsCrypto && otherIsCrypto) {
                                otherTx.amount 
                            } else {
                                -safeTx.amount
                            }

                            val updatedOtherTx = otherTx.copy(
                                title = safeTx.title,
                                amount = newOtherAmount,
                                date = safeTx.date,
                                isRecurring = safeTx.isRecurring,
                                recurrenceInterval = safeTx.recurrenceInterval,
                                endDate = safeTx.endDate,
                                investmentEur = safeTx.investmentEur,
                                feesPercent = safeTx.feesPercent
                            )
                            otherAccountRef.document(doc.id).set(updatedOtherTx)
                        }
                    }
                }
            } catch (e: Exception) {
                getPlatform().log("TX_EDIT_ERROR", "Erreur lors de la mise à jour : ${e.message}")
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
        cryptoRate: Double? = null,
        familyCategory: String? = null,
        subCategory: String? = null
    ) {
        viewModelScope.launch {
            try {
                val sourceAccDoc = db.collection("accounts").document(sourceAccountId).get()
                val targetAccDoc = db.collection("accounts").document(targetAccountId).get()
                
                val sourceIsCrypto = try { sourceAccDoc.get<String?>("type") == "CRYPTO" } catch (e: Exception) { false }
                val targetIsCrypto = try { targetAccDoc.get<String?>("type") == "CRYPTO" } catch (e: Exception) { false }
                
                val absSaisi = abs(amount)
                val transferGroupId = generateRandomId()
                
                val rate = cryptoRate ?: 1.0

                val (sourceVal, targetVal, fiatVal) = when {
                    sourceIsCrypto && !targetIsCrypto -> {
                        val btcQty = absSaisi
                        val eurVal = investmentEur ?: (btcQty * rate)
                        Triple(btcQty, eurVal, eurVal)
                    }
                    !sourceIsCrypto && targetIsCrypto -> {
                        val eurVal = absSaisi
                        val btcQty = investmentEur?.let { if (rate > 0) it / rate else 0.0 } ?: (eurVal / rate)
                        Triple(eurVal, btcQty, eurVal)
                    }
                    else -> {
                        Triple(absSaisi, absSaisi, investmentEur ?: absSaisi)
                    }
                }

                val safeDate = DateTimeUtils.toSafeInstant(date.toInstant()).toTimestamp()
                val outTx = Transaction(
                    title = title,
                    amount = -sourceVal,
                    type = "TRANSFER",
                    familyCategory = familyCategory ?: "Virement",
                    subCategory = subCategory ?: "Virement interne",
                    date = safeDate,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = targetAccountId,
                    investmentEur = if (sourceIsCrypto) fiatVal else null
                )
                db.collection("accounts").document(sourceAccountId).collection("transactions").add(outTx)

                val inTx = Transaction(
                    title = title,
                    amount = targetVal,
                    type = "TRANSFER",
                    familyCategory = familyCategory ?: "Virement",
                    subCategory = subCategory ?: "Virement interne",
                    date = safeDate,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate,
                    checkedMonths = emptyList(),
                    transferGroupId = transferGroupId,
                    targetAccountId = sourceAccountId,
                    investmentEur = if (targetIsCrypto) fiatVal else null,
                    feesPercent = if (targetIsCrypto) feesPercent else null
                )
                db.collection("accounts").document(targetAccountId).collection("transactions").add(inTx)
            } catch (e: Exception) { }
        }
    }

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
        val safeDate = DateTimeUtils.toSafeInstant(newDate.toInstant()).toTimestamp()
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
                        date = safeDate,
                        isRecurring = newIsRecurring,
                        recurrenceInterval = newRecurrenceInterval,
                        endDate = newEndDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent
                    )
                    transactionsRef.document(oldTransaction.id).set(updated)
                    return@launch
                }

                val effectiveInstant = effectiveDate.toInstant()
                val currentMonthIndex = DateTimeUtils.getYear(effectiveInstant) * 12 + DateTimeUtils.getMonth(effectiveInstant)

                when (scope) {
                    RecurrenceEditScope.ALL -> {
                        val updated = oldTransaction.copy(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = safeDate,
                            isRecurring = newIsRecurring,
                            recurrenceInterval = newRecurrenceInterval,
                            endDate = newEndDate,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        transactionsRef.document(oldTransaction.id).set(updated)

                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.where { "transferGroupId" equalTo oldTransaction.transferGroupId }.get()
                            for (doc in otherTxSnapshot.documents) {
                                val otherTx = doc.data<Transaction>().copy(id = doc.id)
                                val updatedOther = otherTx.copy(
                                    title = newTitle,
                                    amount = -newAmount,
                                    date = safeDate,
                                    isRecurring = newIsRecurring,
                                    recurrenceInterval = newRecurrenceInterval,
                                    endDate = newEndDate,
                                    investmentEur = investmentEur,
                                    feesPercent = feesPercent
                                )
                                otherAccountRef.document(doc.id).set(updatedOther)
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_AND_FUTURE -> {
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

                        transactionsRef.document(oldTransaction.id)
                            .update("endDate" to effectiveDate, "checkedMonths" to oldPointages)

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) generateRandomId() else null

                        val brandNewTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = safeDate,
                            isRecurring = newIsRecurring,
                            recurrenceInterval = newRecurrenceInterval,
                            endDate = newEndDate,
                            checkedMonths = newPointages,
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        transactionsRef.add(brandNewTx)

                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.where { "transferGroupId" equalTo oldTransaction.transferGroupId }.get()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate" to effectiveDate)
                            }

                            val otherNewTx = brandNewTx.copy(
                                amount = -newAmount,
                                targetAccountId = accountId,
                                checkedMonths = emptyList()
                            )
                            otherAccountRef.add(otherNewTx)
                        }
                    }

                    RecurrenceEditScope.THIS_ONLY -> {
                        val currentMonthKey = "${DateTimeUtils.getYear(effectiveInstant)}-${(DateTimeUtils.getMonth(effectiveInstant) + 1).toString().padStart(2, '0')}"
                        val isThisMonthChecked = oldTransaction.isCheckedForMonth(currentMonthKey)
                        
                        val oldPointages = oldTransaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }
                        
                        val futurePointages = oldTransaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m > currentMonthIndex
                            } catch (e: Exception) { false }
                        }

                        transactionsRef.document(oldTransaction.id)
                            .update("endDate" to effectiveDate, "checkedMonths" to oldPointages)

                        val newTransferGroupId = if (!oldTransaction.transferGroupId.isNullOrBlank()) generateRandomId() else null

                        val isolatedTx = Transaction(
                            title = newTitle,
                            amount = newAmount,
                            familyCategory = newFamilyCategory,
                            subCategory = newSubCategory,
                            type = newType,
                            paymentMethod = newPaymentMethod,
                            date = safeDate, 
                            isRecurring = false,
                            checkedMonths = if (isThisMonthChecked) listOf(currentMonthKey) else emptyList(),
                            transferGroupId = newTransferGroupId,
                            targetAccountId = oldTransaction.targetAccountId,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        transactionsRef.add(isolatedTx)

                        val step = when {
                            oldTransaction.recurrenceInterval == "QUARTERLY" -> 3
                            oldTransaction.recurrenceInterval == "ANNUAL" -> 12
                            oldTransaction.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                                oldTransaction.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                            }
                            else -> 1
                        }
                        val effectiveStep = if (step < 1) 1 else step
                        val futureInstant = DateTimeUtils.addMonths(effectiveInstant, effectiveStep)
                        val futureTimestamp = Timestamp(futureInstant.epochSeconds, futureInstant.nanosecondsOfSecond)

                        val remainingSeriesTx = oldTransaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = oldTransaction.endDate,
                            checkedMonths = futurePointages
                        )
                        transactionsRef.add(remainingSeriesTx)

                        if (!oldTransaction.transferGroupId.isNullOrBlank() && !oldTransaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(oldTransaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.where { "transferGroupId" equalTo oldTransaction.transferGroupId }.get()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate" to effectiveDate)
                            }

                            val otherIsolatedTx = isolatedTx.copy(amount = -newAmount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherIsolatedTx)

                            val otherRemainingSeriesTx = remainingSeriesTx.copy(amount = -remainingSeriesTx.amount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherRemainingSeriesTx)
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun deleteRecurringTransactionWithScope(
        accountId: String,
        transaction: Transaction,
        effectiveDate: Timestamp,
        scope: RecurrenceEditScope
    ) {
        viewModelScope.launch {
            try {
                val transactionsRef = db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")

                if (!transaction.isRecurring) {
                    transactionsRef.document(transaction.id).delete()
                    return@launch
                }

                val effectiveInstant = effectiveDate.toInstant()
                val currentMonthIndex = DateTimeUtils.getYear(effectiveInstant) * 12 + DateTimeUtils.getMonth(effectiveInstant)

                when (scope) {
                    RecurrenceEditScope.ALL -> {
                        transactionsRef.document(transaction.id).delete()
                        
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.where { "transferGroupId" equalTo transaction.transferGroupId }.get()
                            for (doc in otherTxSnapshot.documents) {
                                otherAccountRef.document(doc.id).delete()
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_AND_FUTURE -> {
                        val oldPointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }

                        transactionsRef.document(transaction.id)
                            .update("endDate" to effectiveDate, "checkedMonths" to oldPointages)
                        
                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            val otherTxSnapshot = otherAccountRef.where { "transferGroupId" equalTo transaction.transferGroupId }.get()
                            for (doc in otherTxSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate" to effectiveDate)
                            }
                        }
                    }

                    RecurrenceEditScope.THIS_ONLY -> {
                        val oldPointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m < currentMonthIndex
                            } catch (e: Exception) { true }
                        }

                        val futurePointages = transaction.checkedMonths.filter { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                y * 12 + m > currentMonthIndex
                            } catch (e: Exception) { false }
                        }

                        transactionsRef.document(transaction.id)
                            .update("endDate" to effectiveDate, "checkedMonths" to oldPointages)

                        val step = when {
                            transaction.recurrenceInterval == "QUARTERLY" -> 3
                            transaction.recurrenceInterval == "ANNUAL" -> 12
                            transaction.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                                transaction.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                            }
                            else -> 1
                        }
                        val effectiveStep = if (step < 1) 1 else step
                        val futureInstant = DateTimeUtils.addMonths(effectiveInstant, effectiveStep)
                        val futureTimestamp = Timestamp(futureInstant.epochSeconds, futureInstant.nanosecondsOfSecond)

                        val remainingSeriesTx = transaction.copy(
                            id = "",
                            date = futureTimestamp,
                            endDate = transaction.endDate,
                            checkedMonths = futurePointages
                        )
                        transactionsRef.add(remainingSeriesTx)

                        if (!transaction.transferGroupId.isNullOrBlank() && !transaction.targetAccountId.isNullOrBlank()) {
                            val otherAccountRef = db.collection("accounts")
                                .document(transaction.targetAccountId)
                                .collection("transactions")
                            
                            val otherOldSnapshot = otherAccountRef.where { "transferGroupId" equalTo transaction.transferGroupId }.get()
                            for (doc in otherOldSnapshot.documents) {
                                otherAccountRef.document(doc.id).update("endDate" to effectiveDate)
                            }

                            val otherRemainingSeriesTx = remainingSeriesTx.copy(amount = -remainingSeriesTx.amount, targetAccountId = accountId, checkedMonths = emptyList())
                            otherAccountRef.add(otherRemainingSeriesTx)
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private suspend fun clearExistingLoanData(accountId: String, linkedAccountId: String?) {
        val selfTxs = db.collection("accounts").document(accountId).collection("transactions").get()
        for (doc in selfTxs.documents) {
            db.collection("accounts").document(accountId).collection("transactions").document(doc.id).delete()
        }
        
        if (linkedAccountId != null) {
            val linkedTxs = db.collection("accounts").document(linkedAccountId).collection("transactions")
                .where { "targetAccountId" equalTo accountId }
                .get()
            for (doc in linkedTxs.documents) {
                db.collection("accounts").document(linkedAccountId).collection("transactions").document(doc.id).delete()
            }
        }
    }

    fun generateLoanInstallments(
        account: Account,
        startDateInstant: Instant,
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
                
                val outputFormat = "dd/MM/yyyy"
                val startStr = DateTimeUtils.formatDate(startDateInstant, outputFormat)
                
                val updates = mutableMapOf<String, Any?>(
                    "totalAmount" to totalCapital,
                    "loanStartDate" to startStr,
                    "loanMonthlyPayment" to monthlyPayment,
                    "loanInsurance" to insurance,
                    "loanRate" to rate,
                    "loanTotalRepayment" to (monthlyPayment + insurance) * durationMonths
                )
                
                val endInstant = DateTimeUtils.addMonths(startDateInstant, durationMonths - 1)
                updates["loanEndDate"] = DateTimeUtils.formatDate(endInstant, outputFormat)

                db.collection("accounts").document(account.id).update(updates)

                val currentMonthStart = DateTimeUtils.startOfMonth(DateTimeUtils.now())
                val totalMonthly = monthlyPayment + insurance
                val transferGroupIdPrefix = "LOAN_${account.id}_"
                
                var remainingCapital = totalCapital
                val monthlyRate = (rate / 100.0) / 12.0

                var iterInstant = startDateInstant

                for (i in 0 until durationMonths) {
                    val interestPart = if (monthlyRate > 0) remainingCapital * monthlyRate else 0.0
                    val principalRepaid = monthlyPayment - interestPart
                    
                    val currentOccurrence = DateTimeUtils.toSafeInstant(DateTimeUtils.addMonths(startDateInstant, i))
                    val date = Timestamp(currentOccurrence.epochSeconds, currentOccurrence.nanosecondsOfSecond)
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
                    db.collection("accounts").document(account.id).collection("transactions").add(creditTx)

                    remainingCapital -= principalRepaid
                    if (remainingCapital < 0) remainingCapital = 0.0

                    if (account.linkedAccountId != null && currentOccurrence >= currentMonthStart) {
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
                        db.collection("accounts").document(account.linkedAccountId).collection("transactions").add(debitTx)
                    }
                }
                
                _importStatus.value = "Échéancier généré : $durationMonths mensualités créées."
                
            } catch (e: Exception) {
                _importStatus.value = "Erreur lors de la génération : ${e.message}"
            }
        }
    }

    fun updateLoanMetadata(accountId: String, totalCapital: Double, accountName: String) {
        viewModelScope.launch {
            try {
                val accountRef = db.collection("accounts").document(accountId)
                val accountData = accountRef.get()
                
                // Lecture sécurisée du montant précédent
                val oldAmount = try { 
                    accountData.get<Double?>("totalAmount") ?: 0.0 
                } catch (e: Exception) {
                    0.0
                }
                
                val diff = totalCapital - oldAmount

                // Mise à jour du capital sur le compte
                accountRef.update("totalAmount" to totalCapital)
                
                // Mise à jour des transactions du compte (échéancier)
                val selfTxs = accountRef.collection("transactions").get()
                for (doc in selfTxs.documents) {
                    try {
                        val updates = mutableMapOf<String, Any?>()
                        updates["title"] = accountName
                        
                        val currentRemaining = try { 
                            doc.get<Double?>("remainingDebt") 
                        } catch (e: Exception) { 
                            null 
                        }
                        
                        if (currentRemaining != null) {
                            updates["remainingDebt"] = currentRemaining + diff
                        }
                        accountRef.collection("transactions").document(doc.id).update(updates)
                    } catch (e: Exception) {
                        getPlatform().log("LOAN_SYNC_ERROR", "Erreur tx ${doc.id}: ${e.message}")
                    }
                }
                
                // Mise à jour des libellés sur le compte lié (débits de mensualités)
                val linkedId = try { accountData.get<String?>("linkedAccountId") } catch (e: Exception) { null }
                if (!linkedId.isNullOrBlank()) {
                    val linkedAccountRef = db.collection("accounts").document(linkedId)
                    val allLinkedTxs = linkedAccountRef.collection("transactions").get()
                        
                    for (doc in allLinkedTxs.documents) {
                        try {
                            val title = doc.get<String?>("title") ?: ""
                            val targetId = doc.get<String?>("targetAccountId")
                            
                            if (targetId == accountId || 
                                title.contains("Prélèvement", ignoreCase = true) || 
                                title.contains("Mensualité", ignoreCase = true) ||
                                title.contains("Echéance", ignoreCase = true)) {
                                linkedAccountRef.collection("transactions").document(doc.id).update("title" to accountName)
                            }
                        } catch (e: Exception) { }
                    }
                }
                _importStatus.value = "Capital et données synchronisés."
            } catch (e: Exception) {
                getPlatform().log("LOAN_UPDATE_ERROR", e.message ?: "Erreur inconnue")
                _importStatus.value = "Erreur de mise à jour."
            }
        }
    }

    private val _pdfRows = MutableStateFlow<List<PdfRow>>(emptyList())
    val pdfRows: StateFlow<List<PdfRow>> = _pdfRows.asStateFlow()

    fun extractPdfData(uri: String) {
        viewModelScope.launch {
            try {
                _importStatus.value = "Analyse spatiale du document..."
                val importer = getPdfImporter()
                val rows = importer.extractTableData(uri)
                _pdfRows.value = rows
                _importStatus.value = if (rows.isNotEmpty()) "Prêt à identifier les colonnes." else "Erreur : PDF vide"
            } catch (e: Exception) {
                _importStatus.value = "Erreur lecture PDF : ${e.message}"
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
                
                val currentMonthStart = DateTimeUtils.startOfMonth(DateTimeUtils.now())
                
                val accountData = db.collection("accounts").document(accountId).get()
                val accountName = accountData.get<String>("name") ?: "Prêt"
                
                var importedCount = 0
                var firstInstant: Instant? = null
                var lastInstant: Instant? = null
                val amountValues = mutableListOf<Double>()

                rows.drop(startRowIdx).forEachIndexed { index, pdfRow ->
                    val dateCell = pdfRow.cells.find { it.colIndex == dateColIdx }
                    val amountCell = pdfRow.cells.find { it.colIndex == amountColIdx }
                    val principalCell = if (principalColIdx != -1) pdfRow.cells.find { it.colIndex == principalColIdx } else null
                    val interestCell = if (interestColIdx != -1) pdfRow.cells.find { it.colIndex == interestColIdx } else null
                    val insuranceCell = if (insuranceColIdx != -1) pdfRow.cells.find { it.colIndex == insuranceColIdx } else null
                    val debtCell = if (remainingDebtColIdx != -1) pdfRow.cells.find { it.colIndex == remainingDebtColIdx } else null

                    val rawDateText = dateCell?.text?.trim() ?: ""
                    val instant = parseFlexibleDate(rawDateText)
                    
                    val amount = amountCell?.text?.cleanAmount() ?: 0.0

                    if (instant != null && amount > 0) {
                        val safeInstant = DateTimeUtils.toSafeInstant(instant)
                        amountValues.add(amount)
                        if (firstInstant == null) firstInstant = safeInstant
                        lastInstant = safeInstant

                        val currentGroupId = "LOAN_PDF_${accountId}_${index}"
                        
                        val creditTx = Transaction(
                            title = accountName,
                            amount = amount,
                            type = "INCOME",
                            familyCategory = "Crédit",
                            subCategory = "Amortissement",
                            date = Timestamp(safeInstant.epochSeconds, safeInstant.nanosecondsOfSecond),
                            checkedMonths = emptyList(),
                            principalPart = principalCell?.text?.cleanAmount(),
                            interestPart = interestCell?.text?.cleanAmount(),
                            insurancePart = insuranceCell?.text?.cleanAmount(),
                            remainingDebt = debtCell?.text?.cleanAmount(),
                            transferGroupId = currentGroupId,
                            targetAccountId = linkedAccountId
                        )
                        db.collection("accounts").document(accountId).collection("transactions").add(creditTx)

                        if (linkedAccountId != null && safeInstant >= currentMonthStart) {
                            val debitTx = Transaction(
                                title = accountName,
                                amount = -amount,
                                type = "EXPENSE",
                                familyCategory = "Crédit",
                                subCategory = "Mensualité",
                                date = Timestamp(safeInstant.epochSeconds, safeInstant.nanosecondsOfSecond),
                                checkedMonths = emptyList(),
                                transferGroupId = currentGroupId,
                                targetAccountId = accountId
                            )
                            db.collection("accounts").document(linkedAccountId).collection("transactions").add(debitTx)
                        }
                        importedCount++
                    }
                }

                if (importedCount > 0) {
                    val updates = mutableMapOf<String, Any?>()
                    val outputFormat = "dd/MM/yyyy"
                    if (firstInstant != null) updates["loanStartDate"] = DateTimeUtils.formatDate(firstInstant!!, outputFormat)
                    if (lastInstant != null) updates["loanEndDate"] = DateTimeUtils.formatDate(lastInstant!!, outputFormat)
                    
                    val totalRepayment = amountValues.sum()
                    val commonAmount = amountValues.groupBy { it }.maxByOrNull { it.value.size }?.key
                    
                    if (commonAmount != null) updates["loanMonthlyPayment"] = commonAmount
                    updates["loanTotalRepayment"] = totalRepayment
                    
                    db.collection("accounts").document(accountId).update(updates)
                    _importStatus.value = "Importation réussie : $importedCount mensualités."
                } else {
                    _importStatus.value = "Erreur : Aucune donnée valide trouvée."
                }
                clearPdfData()
            } catch (e: Exception) {
                _importStatus.value = "Erreur import : ${e.message}"
            }
        }
    }

    private fun parseFlexibleDate(text: String): Instant? {
        // Simple parser for dd/MM/yyyy or dd/MM/yy
        try {
            val parts = text.split("/")
            if (parts.size != 3) return null
            val day = parts[0].toInt()
            val month = parts[1].toInt()
            var year = parts[2].toInt()
            if (year < 100) year += 2000
            return LocalDateTime(year, month, day, 12, 0, 0).toInstant(TimeZone.UTC)
        } catch (e: Exception) { return null }
    }

    private fun String.cleanAmount(): Double {
        val cleaned = this.replace(Regex("[^0-9,.-]"), "").replace(",", ".")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun generateRandomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}
