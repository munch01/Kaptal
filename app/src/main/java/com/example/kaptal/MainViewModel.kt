package com.example.kaptal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Account
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- ÉTATS DE L'UI ---
sealed interface AccountsUiState {
    object Loading : AccountsUiState
    data class Success(val accounts: List<Account>) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

class MainViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- StateFlow principal pour les comptes ---
    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    // --- StateFlow pour la devise globale de l'application ---
    private val _appCurrency = MutableStateFlow("€")
    val appCurrency: StateFlow<String> = _appCurrency.asStateFlow()

    // --- StateFlow pour le compte sélectionné ---
    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    init {
        loadAccounts()
        loadUserSettings()
    }

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
    }

    // --- CHARGEMENT DES COMPTES (Sécurisé par UID) ---
    fun loadAccounts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = AccountsUiState.Error("Utilisateur non connecté.")
            return
        }

        _uiState.value = AccountsUiState.Loading

        // Utilisation de l'UID pour filtrer les comptes de l'utilisateur
        firestore.collection("accounts")
            .whereArrayContains("members", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = AccountsUiState.Error(error.localizedMessage ?: "Erreur de chargement")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val accountsList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Account::class.java)?.copy(id = doc.id)
                    }.sortedBy { it.order }

                    _uiState.value = AccountsUiState.Success(accountsList)
                }
            }
    }

    // --- CHARGEMENT DES PRÉFÉRENCES (DEVISE) ---
    private fun loadUserSettings() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val currency = snapshot.getString("currency")
                if (!currency.isNullOrBlank()) {
                    _appCurrency.value = currency
                }
            }
        }
    }

    // --- AJOUT D'UN COMPTE (Sécurisé par UID) ---
    fun addAccount(
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String,
        onAccountCreated: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val nextOrder = if (currentState is AccountsUiState.Success) {
                    currentState.accounts.size
                } else {
                    0
                }

                // Ajout de l'UID dans le tableau des membres
                val membersList = mutableListOf(currentUser.uid)

                val newAccountRef = firestore.collection("accounts").document()
                val account = Account(
                    id = newAccountRef.id,
                    name = name,
                    bankName = bankName,
                    initialBalance = initialBalance,
                    type = type,
                    isJoint = isJoint,
                    color = color,
                    order = nextOrder,
                    members = membersList,
                    ownerId = currentUser.uid
                )

                newAccountRef.set(account).await()
                onAccountCreated(account.id)
            } catch (e: Exception) {
                // Gérer l'erreur si nécessaire
            }
        }
    }

    // --- MODIFICATION D'UN COMPTE ---
    fun updateAccount(
        accountId: String,
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "name" to name,
                    "bankName" to bankName,
                    "initialBalance" to initialBalance,
                    "type" to type,
                    "isJoint" to isJoint,
                    "color" to color
                )

                firestore.collection("accounts").document(accountId)
                    .update(updates)
                    .await()

                onSuccess()
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }

    // --- SUPPRESSION D'UN COMPTE ---
    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("accounts").document(accountId).delete().await()
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }

    // --- MISE À JOUR DE L'ORDRE (DRAG & DROP) ---
    fun updateAccountsOrder(newOrderedList: List<Account>) {
        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                newOrderedList.forEachIndexed { index, account ->
                    val docRef = firestore.collection("accounts").document(account.id)
                    batch.update(docRef, "order", index)
                }
                batch.commit().await()
            } catch (e: Exception) {
                // Gérer l'erreur de réordonnancement
            }
        }
    }

    // --- AJOUT D'UN MEMBRE / CO-TITULAIRE (Par e-mail -> Traduction en UID) ---
    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Rechercher l'UID correspondant à l'e-mail dans la collection "users"
                val userQuery = firestore.collection("users")
                    .whereEqualTo("email", memberEmail)
                    .get()
                    .await()

                if (userQuery.isEmpty) {
                    onResult(false, "Aucun utilisateur trouvé avec cet e-mail.")
                    return@launch
                }

                val memberUid = userQuery.documents[0].id

                // 2. Mettre à jour le document du compte avec l'UID trouvé
                val docRef = firestore.collection("accounts").document(accountId)
                val snapshot = docRef.get().await()
                val currentMembers = snapshot.get("members") as? MutableList<String> ?: mutableListOf()

                if (!currentMembers.contains(memberUid)) {
                    currentMembers.add(memberUid)
                    docRef.update("members", currentMembers).await()
                    onResult(true, "Membre $memberEmail ajouté avec succès.")
                } else {
                    onResult(false, "Ce membre fait déjà partie du compte.")
                }
            } catch (e: Exception) {
                onResult(false, "Erreur lors de l'ajout du membre : ${e.localizedMessage}")
            }
        }
    }
}