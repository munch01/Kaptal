package com.Muncho.kaptal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.getPlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccountDetailViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private var currentAccountId: String? = null
    private var listenerJob: kotlinx.coroutines.Job? = null

    fun loadTransactions(accountId: String) {
        if (accountId.isEmpty()) return
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
        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .add(transaction)
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

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}
