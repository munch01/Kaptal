package com.example.kaptal

import androidx.lifecycle.ViewModel
import com.example.kaptal.model.Account
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AccountsUiState {
    object Loading : AccountsUiState()
    data class Success(val accounts: List<Account>) : AccountsUiState()
    data class Error(val message: String) : AccountsUiState()
}

class MainViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = AccountsUiState.Error("Utilisateur non connecté")
            return
        }

        _uiState.value = AccountsUiState.Loading

        firestore.collection("users")
            .document(userId)
            .collection("accounts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = AccountsUiState.Error(error.localizedMessage ?: "Erreur inconnue")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val accounts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Account::class.java)?.copy(id = doc.id)
                    }
                    _uiState.value = AccountsUiState.Success(accounts)
                }
            }
    }

    // --- AJOUTER UN COMPTE ---
    fun addAccount(name: String, bankName: String, initialBalance: Double, type: String, isJoint: Boolean, onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        val newAccount = hashMapOf(
            "name" to name,
            "bankName" to bankName,
            "initialBalance" to initialBalance,
            "type" to type,
            "isJoint" to isJoint,
            "currency" to "€"
        )

        firestore.collection("users")
            .document(userId)
            .collection("accounts")
            .add(newAccount)
            .addOnSuccessListener {
                onComplete()
            }
    }

    // --- METTRE À JOUR UN COMPTE ---
    fun updateAccount(accountId: String, name: String, bankName: String, initialBalance: Double, type: String, isJoint: Boolean, onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        val updatedData = mapOf(
            "name" to name,
            "bankName" to bankName,
            "initialBalance" to initialBalance,
            "type" to type,
            "isJoint" to isJoint
        )

        firestore.collection("users")
            .document(userId)
            .collection("accounts")
            .document(accountId)
            .update(updatedData)
            .addOnSuccessListener {
                onComplete()
            }
    }

    // --- SUPPRIMER UN COMPTE ---
    fun deleteAccount(accountId: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("accounts")
            .document(accountId)
            .delete()
    }
}