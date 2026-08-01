package com.example.kaptal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Account
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    // --- GESTION DU COMPTE SÉLECTIONNÉ POUR LA NAVIGATION ---
    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
    }

    init {
        saveCurrentUserToFirestore() // S'assure que l'utilisateur actuel a sa fiche dans "users"
        loadAccounts()
    }

    // --- ENREGISTRER L'UTILISATEUR CONNECTÉ DANS FIRESTORE ---
    private fun saveCurrentUserToFirestore() {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.email != null) {
            val userId = currentUser.uid
            val userEmail = currentUser.email!!.trim().lowercase()

            val userMap = hashMapOf(
                "email" to userEmail
            )

            firestore.collection("users")
                .document(userId)
                .set(userMap, SetOptions.merge())
        }
    }

    fun loadAccounts() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = AccountsUiState.Error("Utilisateur non connecté")
            return
        }

        _uiState.value = AccountsUiState.Loading

        // On écoute la collection globale "accounts" où l'utilisateur fait partie des membres
        firestore.collection("accounts")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = AccountsUiState.Error(error.localizedMessage ?: "Erreur inconnue")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val accounts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Account::class.java)?.copy(id = doc.id)
                    }.sortedBy { it.order }
                    _uiState.value = AccountsUiState.Success(accounts)
                }
            }
    }

    // --- AJOUTER UN COMPTE ---
    fun addAccount(
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String,
        onComplete: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return

        // Le compte intègre l'ID du créateur dans le tableau "members"
        val newAccount = hashMapOf(
            "name" to name,
            "bankName" to bankName,
            "initialBalance" to initialBalance,
            "type" to type,
            "isJoint" to isJoint,
            "color" to color,
            "currency" to "€",
            "order" to 0,
            "members" to listOf(userId)
        )

        firestore.collection("accounts")
            .add(newAccount)
            .addOnSuccessListener { documentReference ->
                onComplete(documentReference.id)
            }
    }

    // --- METTRE À JOUR UN COMPTE ---
    fun updateAccount(
        accountId: String,
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String,
        onComplete: () -> Unit
    ) {
        val updatedData = mapOf(
            "name" to name,
            "bankName" to bankName,
            "initialBalance" to initialBalance,
            "type" to type,
            "isJoint" to isJoint,
            "color" to color
        )

        firestore.collection("accounts")
            .document(accountId)
            .update(updatedData)
            .addOnSuccessListener {
                onComplete()
            }
    }

    // --- METTRE À JOUR L'ORDRE DES COMPTES ---
    fun updateAccountsOrder(accounts: List<Account>) {
        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                accounts.forEachIndexed { index, account ->
                    val docRef = firestore.collection("accounts").document(account.id)
                    batch.update(docRef, "order", index)
                }
                batch.commit().await()
            } catch (e: Exception) {
                // Gérer l'erreur silencieusement ou via l'UI si besoin
            }
        }
    }

    // --- SUPPRIMER UN COMPTE ---
    fun deleteAccount(accountId: String) {
        firestore.collection("accounts")
            .document(accountId)
            .delete()
    }

    // --- RECHERCHER ET AJOUTER UN CO-TITULAIRE PAR EMAIL ---
    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        firestore.collection("users")
            .whereEqualTo("email", memberEmail.trim().lowercase())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onResult(false, "Aucun utilisateur trouvé avec cet e-mail.")
                } else {
                    val targetUserId = snapshot.documents.first().id
                    firestore.collection("accounts")
                        .document(accountId)
                        .update("members", FieldValue.arrayUnion(targetUserId))
                        .addOnSuccessListener {
                            onResult(true, "Co-titulaire ajouté avec succès !")
                        }
                        .addOnFailureListener { e ->
                            onResult(false, e.localizedMessage ?: "Erreur lors du partage.")
                        }
                }
            }
            .addOnFailureListener { e ->
                onResult(false, e.localizedMessage ?: "Erreur de recherche.")
            }
    }
}