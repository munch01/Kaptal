package com.example.kaptal.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

class AccountDetailViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(getCurrentYearMonth())
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    fun loadTransactions(accountId: String) {
        if (accountId.isEmpty()) return

        db.collection("accounts")
            .document(accountId)
            .collection("transactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreError", "Erreur lors du chargement des transactions", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                    }
                    Log.d("FirestoreDebug", "Transactions chargées : ${list.size} pour le compte $accountId")
                    _transactions.value = list
                }
            }
    }

    fun changeMonth(year: Int, month: Int) {
        _selectedYearMonth.value = Pair(year, month)
    }

    fun toggleTransactionCheck(accountId: String, transactionId: String, newCheckedStatus: Boolean) {
        if (accountId.isEmpty() || transactionId.isEmpty()) return

        _transactions.value = _transactions.value.map { tx ->
            if (tx.id == transactionId) tx.copy(isChecked = newCheckedStatus) else tx
        }

        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transactionId)
                    .update("isChecked", newCheckedStatus)
                    .await()
            } catch (e: Exception) {
                Log.e("FirestoreError", "Erreur lors de la mise à jour de la case à cocher", e)
                loadTransactions(accountId)
            }
        }
    }

    fun addTransaction(accountId: String, transaction: Transaction) {
        viewModelScope.launch {
            try {
                val collectionRef = db.collection("accounts").document(accountId).collection("transactions")

                if (transaction.isRecurring) {
                    val groupId = UUID.randomUUID().toString()
                    val firstTransaction = transaction.copy(recurrenceGroupId = groupId)

                    // 1. On ajoute la première occurrence
                    collectionRef.add(firstTransaction).await()

                    val cal = Calendar.getInstance().apply { time = transaction.date.toDate() }
                    val endCal = transaction.endDate?.let {
                        Calendar.getInstance().apply { time = it.toDate() }
                    }

                    var monthsCount = 0
                    val maxInfiniteMonths = 24 // Limite de sécurité pour les récurrences infinies

                    while (true) {
                        cal.add(Calendar.MONTH, 1)
                        monthsCount++

                        // Arrêt si on dépasse la date de fin explicite
                        if (endCal != null && cal.after(endCal)) {
                            break
                        }

                        // Arrêt de sécurité si pas de date de fin (infini)
                        if (endCal == null && monthsCount > maxInfiniteMonths) {
                            break
                        }

                        val nextDate = Timestamp(cal.time)
                        val recurringInstance = transaction.copy(
                            id = "",
                            date = nextDate,
                            isChecked = false,
                            recurrenceGroupId = groupId
                        )
                        collectionRef.add(recurringInstance).await()
                    }
                } else {
                    collectionRef.add(transaction).await()
                }
                Log.d("FirestoreDebug", "Transaction(s) ajoutée(s) avec succès")
            } catch (e: Exception) {
                Log.e("FirestoreError", "Erreur lors de l'ajout de la transaction", e)
            }
        }
    }

    fun updateTransaction(accountId: String, transaction: Transaction) {
        if (transaction.id.isEmpty()) return

        viewModelScope.launch {
            try {
                db.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .document(transaction.id)
                    .set(transaction)
                    .await()
                Log.d("FirestoreDebug", "Transaction mise à jour avec succès")
            } catch (e: Exception) {
                Log.e("FirestoreError", "Erreur lors de la mise à jour de la transaction", e)
            }
        }
    }

    private fun getCurrentYearMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }
}