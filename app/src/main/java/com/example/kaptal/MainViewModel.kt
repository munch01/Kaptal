package com.example.kaptal

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Account
import com.example.kaptal.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// --- ÉTATS DE L'UI ---
sealed interface AccountsUiState {
    object Loading : AccountsUiState
    data class Success(val accounts: List<Account>) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

class MainViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- SAUVEGARDE DE LA POSITION DU PAGER PAR COMPTE ---
    private val savedPagerPositions = mutableStateMapOf<String, Int>()

    fun getSavedPagerPosition(accountId: String): Int {
        return savedPagerPositions[accountId] ?: 120
    }

    fun savePagerPosition(accountId: String, page: Int) {
        savedPagerPositions[accountId] = page
    }

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

    // --- CHARGEMENT DES COMPTES (Sécurisé par UID avec auto-retry en cas de latence du jeton) ---
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
                    // Si l'erreur est liée aux permissions (souvent due au jeton non prêt au tout premier boot)
                    val errorMessage = error.localizedMessage ?: ""
                    if (errorMessage.contains("PERMISSION_DENIED", ignoreCase = true) ||
                        errorMessage.contains("permissions", ignoreCase = true)) {

                        // On retente automatiquement après 1 seconde le temps que le jeton se propage
                        viewModelScope.launch {
                            delay(1000)
                            loadAccounts()
                        }
                    } else {
                        _uiState.value = AccountsUiState.Error(errorMessage.ifBlank { "Erreur de chargement" })
                    }
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

    // --- AJOUT D'UN COMPTE (Sécurisé par UID avec génération du solde initial) ---
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

                // 1. Enregistrement du compte
                newAccountRef.set(account).await()

                // 2. Si le solde initial n'est pas nul, on crée automatiquement la transaction initiale
                if (initialBalance != 0.0) {
                    val transactionRef = newAccountRef.collection("transactions").document()
                    val initialTransaction = Transaction(
                        id = transactionRef.id,
                        title = "Solde initial",
                        amount = initialBalance,
                        type = if (initialBalance >= 0) "INCOME" else "EXPENSE",
                        category = "Divers",
                        paymentMethod = "Virement",
                        date = Timestamp(Date()),
                        isChecked = true // Pointé par défaut pour alimenter immédiatement le solde réel
                    )
                    transactionRef.set(initialTransaction).await()
                }

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

    // --- AJOUT D'UN MEMBRE / CO-TITULAIRE ---
    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val userQuery = firestore.collection("users")
                    .whereEqualTo("email", memberEmail)
                    .get()
                    .await()

                if (userQuery.isEmpty) {
                    onResult(false, "Aucun utilisateur trouvé avec cet e-mail.")
                    return@launch
                }

                val memberUid = userQuery.documents[0].id

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