package com.example.kaptal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.data.FirestoreRepository
import com.example.kaptal.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// États possibles pour l'interface utilisateur
sealed class AccountsUiState {
    object Loading : AccountsUiState()
    data class Success(val accounts: List<Account>) : AccountsUiState()
    data class Error(val message: String) : AccountsUiState()
}

class MainViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    /**
     * Charger les comptes depuis Firestore
     */
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.value = AccountsUiState.Loading
            val result = repository.getAccounts()

            result.onSuccess { accountsList ->
                _uiState.value = AccountsUiState.Success(accountsList)
            }.onFailure { exception ->
                _uiState.value = AccountsUiState.Error(
                    exception.message ?: "Erreur lors du chargement des comptes"
                )
            }
        }
    }

    /**
     * Ajouter un nouveau compte bancaire
     */
    fun addAccount(
        name: String,
        balance: Double,
        type: String = "CHECKING",
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val newAccount = Account(
                name = name,
                initialBalance = balance,
                type = type
            )
            val result = repository.addAccount(newAccount)
            if (result.isSuccess) {
                loadAccounts() // Rafraîchissement automatique de la liste
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    /**
     * Supprimer un compte bancaire individuel
     */
    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            val result = repository.deleteAccount(accountId)
            if (result.isSuccess) {
                loadAccounts() // Rafraîchissement automatique après suppression
            }
        }
    }
}