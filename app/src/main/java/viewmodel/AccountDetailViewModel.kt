package com.example.kaptal.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transaction.id)
                    .set(transaction)
                    .await()
            } catch (e: Exception) {
                Log.e("UPDATE_DEBUG", "Erreur modification transaction", e)
            }
        }
    }

    fun deleteTransaction(
        accountId: String,
        transaction: Transaction,
        effectiveDeleteDate: Timestamp? = null
    ) {
        if (accountId.isEmpty() || transaction.id.isEmpty()) return

        viewModelScope.launch {
            try {
                val transactionsRef = db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")

                if (transaction.isRecurring) {
                    // Si une date effective est passée (date du mois courant), on la prend, sinon on prend transaction.date
                    val deleteCutoffDate = effectiveDeleteDate ?: transaction.date
                    val targetTime = deleteCutoffDate.toDate().time

                    if (!transaction.recurrenceGroupId.isNullOrEmpty()) {
                        // CAS 1.A : Groupe de documents générés physiquement
                        val groupId = transaction.recurrenceGroupId

                        val groupSnapshot = transactionsRef
                            .whereEqualTo("recurrenceGroupId", groupId)
                            .get()
                            .await()

                        val batch = db.batch()

                        groupSnapshot.documents.forEach { doc ->
                            val docTimestamp = doc.getTimestamp("date")
                            if (docTimestamp != null) {
                                val docTime = docTimestamp.toDate().time

                                if (docTime >= targetTime) {
                                    // Supprime à partir de la date ciblée (Octobre et +)
                                    batch.delete(doc.reference)
                                }
                            }
                        }

                        batch.commit().await()
                    } else {
                        // CAS 1.B : Document récurrent unique (virtuel)
                        // On ferme la récurrence à la date sélectionnée (ex: 1er Octobre)
                        transactionsRef.document(transaction.id)
                            .update("endDate", deleteCutoffDate)
                            .await()
                    }
                } else {
                    // CAS 2 : Transaction ponctuelle classique
                    transactionsRef.document(transaction.id).delete().await()
                }
            } catch (e: Exception) {
                Log.e("DELETE_DEBUG", "Erreur lors de la suppression de la transaction", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        currentAccountId = null
    }
}