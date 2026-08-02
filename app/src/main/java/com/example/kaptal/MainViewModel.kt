package com.example.kaptal

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptal.model.Account
import com.example.kaptal.model.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import java.util.Locale

// --- ÉTATS DE L'UI ---
sealed interface AccountsUiState {
    object Loading : AccountsUiState
    data class Success(
        val accounts: List<Account>,
        val accountBalances: Map<String, Double> = emptyMap() // Map: accountId -> soldeRéelActuel
    ) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

class MainViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- ECOUTEURS FIRESTORE ---
    private var accountsListener: ListenerRegistration? = null
    private val transactionsListeners = mutableMapOf<String, ListenerRegistration>()
    private val accountTransactionsMap = mutableMapOf<String, List<Transaction>>()

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

    // --- StateFlow pour les taux de change crypto (ex: "BTC" -> 65000.0) ---
    private val _cryptoRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val cryptoRates: StateFlow<Map<String, Double>> = _cryptoRates.asStateFlow()

    init {
        loadAccounts()
        loadUserSettings()
        fetchCryptoRates()
    }

    // --- RÉCUPÉRATION DES COURS CRYPTO EN DIRECT (COINGECKO) ---
    fun fetchCryptoRates() {
        viewModelScope.launch {
            try {
                val rates = withContext(Dispatchers.IO) {
                    val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana,tether,cardano,ripple&vs_currencies=eur")
                    val jsonString = url.readText()
                    val jsonObject = JSONObject(jsonString)

                    val map = mutableMapOf<String, Double>()
                    if (jsonObject.has("bitcoin")) map["BTC"] = jsonObject.getJSONObject("bitcoin").getDouble("eur")
                    if (jsonObject.has("ethereum")) map["ETH"] = jsonObject.getJSONObject("ethereum").getDouble("eur")
                    if (jsonObject.has("solana")) map["SOL"] = jsonObject.getJSONObject("solana").getDouble("eur")
                    if (jsonObject.has("tether")) map["USDT"] = jsonObject.getJSONObject("tether").getDouble("eur")
                    if (jsonObject.has("cardano")) map["ADA"] = jsonObject.getJSONObject("cardano").getDouble("eur")
                    if (jsonObject.has("ripple")) map["XRP"] = jsonObject.getJSONObject("ripple").getDouble("eur")
                    map
                }
                _cryptoRates.value = rates
            } catch (e: Exception) {
                // En cas d'échec réseau, on garde les anciennes valeurs ou une map vide silencieusement
            }
        }
    }

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
    }

    // --- CHARGEMENT DES COMPTES ---
    fun loadAccounts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = AccountsUiState.Error("Utilisateur non connecté.")
            return
        }

        _uiState.value = AccountsUiState.Loading

        accountsListener?.remove()
        accountsListener = firestore.collection("accounts")
            .whereArrayContains("members", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val errorMessage = error.localizedMessage ?: ""
                    if (errorMessage.contains("PERMISSION_DENIED", ignoreCase = true) ||
                        errorMessage.contains("permissions", ignoreCase = true)) {
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

                    updateTransactionsListeners(accountsList)
                }
            }
    }

    private fun updateTransactionsListeners(accounts: List<Account>) {
        val currentAccountIds = accounts.map { it.id }.toSet()

        transactionsListeners.keys.filter { it !in currentAccountIds }.forEach { id ->
            transactionsListeners[id]?.remove()
            transactionsListeners.remove(id)
            accountTransactionsMap.remove(id)
        }

        if (accounts.isEmpty()) {
            _uiState.value = AccountsUiState.Success(emptyList(), emptyMap())
            return
        }

        for (account in accounts) {
            if (!transactionsListeners.containsKey(account.id)) {
                val listener = firestore.collection("accounts")
                    .document(account.id)
                    .collection("transactions")
                    .addSnapshotListener { txSnapshot, error ->
                        if (error == null && txSnapshot != null) {
                            val txList = txSnapshot.documents.mapNotNull { doc ->
                                doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                            }.sortedByDescending { it.date }

                            accountTransactionsMap[account.id] = txList
                            recalculateBalances(accounts)
                        }
                    }
                transactionsListeners[account.id] = listener
            }
        }

        recalculateBalances(accounts)
    }

    private fun recalculateBalances(accounts: List<Account>) {
        val balancesMap = accounts.associate { account ->
            val txs = accountTransactionsMap[account.id] ?: emptyList()
            account.id to calculateCurrentRealBalance(account, txs)
        }
        _uiState.value = AccountsUiState.Success(accounts, balancesMap)
    }

    private fun calculateCurrentRealBalance(account: Account, transactions: List<Transaction>): Double {
        var total = account.initialBalance
        if (transactions.isEmpty()) return total

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        val minDate = transactions.minOf { it.date.toDate() }
        val minCal = Calendar.getInstance().apply { time = minDate }

        var iterYear = minCal.get(Calendar.YEAR)
        var iterMonth = minCal.get(Calendar.MONTH)

        while (iterYear < currentYear || (iterYear == currentYear && iterMonth <= currentMonth)) {
            val mKey = String.format(Locale.US, "%d-%02d", iterYear, iterMonth + 1)

            for (tx in transactions) {
                if (isTransactionActiveInMonth(tx, iterYear, iterMonth)) {
                    if (tx.isCheckedForMonth(mKey)) {
                        total += tx.amount
                    }
                }
            }

            iterMonth++
            if (iterMonth > 11) {
                iterMonth = 0
                iterYear++
            }
        }

        return total
    }

    private fun isTransactionActiveInMonth(tx: Transaction, year: Int, month: Int): Boolean {
        val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
        val txYear = txCal.get(Calendar.YEAR)
        val txMonth = txCal.get(Calendar.MONTH)

        return if (tx.isRecurring) {
            val startsBeforeOrDuring = (txYear < year) || (txYear == year && txMonth <= month)
            val endsAfterOrDuring = if (tx.endDate != null) {
                val endCal = Calendar.getInstance().apply { time = tx.endDate.toDate() }
                val endYear = endCal.get(Calendar.YEAR)
                val endMonth = endCal.get(Calendar.MONTH)
                (year < endYear) || (year == endYear && month < endMonth)
            } else {
                true
            }
            startsBeforeOrDuring && endsAfterOrDuring
        } else {
            txYear == year && txMonth == month
        }
    }

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
                val nextOrder = if (currentState is AccountsUiState.Success) currentState.accounts.size else 0
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
            } catch (e: Exception) { }
        }
    }

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
                firestore.collection("accounts").document(accountId).update(updates).await()
                onSuccess()
            } catch (e: Exception) { }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("accounts").document(accountId).delete().await()
            } catch (e: Exception) { }
        }
    }

    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val userQuery = firestore.collection("users").whereEqualTo("email", memberEmail).get().await()
                if (userQuery.isEmpty) {
                    onResult(false, "Aucun utilisateur trouvé avec cet e-mail.")
                    return@launch
                }
                val newUserId = userQuery.documents.first().id
                val accountRef = firestore.collection("accounts").document(accountId)

                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(accountRef)
                    val currentMembers = snapshot.get("members") as? List<*> ?: emptyList<Any>()
                    if (!currentMembers.contains(newUserId)) {
                        val updatedMembers = currentMembers + newUserId
                        transaction.update(accountRef, "members", updatedMembers, "isJoint", true)
                    }
                }.await()
                onResult(true, "Membre ajouté avec succès !")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Erreur lors de l'ajout du membre.")
            }
        }
    }

    fun updateAccountsOrder(orderedAccounts: List<Account>) {
        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                orderedAccounts.forEachIndexed { index, account ->
                    val ref = firestore.collection("accounts").document(account.id)
                    batch.update(ref, "order", index)
                }
                batch.commit().await()
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        accountsListener?.remove()
        transactionsListeners.values.forEach { it.remove() }
    }
}