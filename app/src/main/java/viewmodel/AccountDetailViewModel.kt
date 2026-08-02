package com.example.kaptal.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AccountDetailViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    fun loadTransactions(accountId: String) {
        if (accountId.isEmpty()) return
        db.collection("accounts")
            .document(accountId)
            .collection("transactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                    }
                    _transactions.value = list
                }
            }
    }

    fun toggleTransactionCheck(accountId: String, transactionId: String, newCheckedStatus: Boolean) {
        if (accountId.isEmpty() || transactionId.isEmpty()) return

        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transactionId)
                    .update("isChecked", newCheckedStatus)
                    .await()
            } catch (e: Exception) {
                Log.e("CHECK_DEBUG", "Erreur Firestore", e)
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
                Log.e("ADD_DEBUG", "Erreur ajout", e)
            }
        }
    }

    fun updateTransaction(accountId: String, transaction: Transaction) {
        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transaction.id)
                    .set(transaction)
                    .await()
            } catch (e: Exception) {
                Log.e("UPDATE_DEBUG", "Erreur maj", e)
            }
        }
    }
}