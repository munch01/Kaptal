package com.Muncho.kaptal.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Muncho.kaptal.getPlatform
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.network.httpClient
import com.Muncho.kaptal.utils.DateTimeUtils
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.Direction
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

sealed interface AccountsUiState {
    object Loading : AccountsUiState
    data class Success(
        val accounts: List<Account>,
        val accountBalances: Map<String, Double> = emptyMap(),
        val creditRemainingDebts: Map<String, Double> = emptyMap()
    ) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

class MainViewModel : ViewModel() {

    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    private val transactionsListeners = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val accountTransactionsMap = mutableMapOf<String, List<Transaction>>()
    private var lastAccountsList: List<Account> = emptyList()

    private val savedPagerPositions = mutableStateMapOf<String, Int>()

    fun getSavedPagerPosition(accountId: String): Int {
        return savedPagerPositions[accountId] ?: 120
    }

    fun savePagerPosition(accountId: String, page: Int) {
        savedPagerPositions[accountId] = page
    }

    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _appCurrency = MutableStateFlow("€")
    val appCurrency: StateFlow<String> = _appCurrency.asStateFlow()

    private val _cryptoRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val cryptoRates: StateFlow<Map<String, Double>> = _cryptoRates.asStateFlow()

    private val _livretARate = MutableStateFlow(3.0)
    val livretARate: StateFlow<Double> = _livretARate.asStateFlow()

    val popularCryptos = listOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "SOL" to "Solana",
        "XRP" to "Ripple", "ADA" to "Cardano", "DOT" to "Polkadot",
        "DOGE" to "Dogecoin", "AVAX" to "Avalanche", "MATIC" to "Polygon",
        "LINK" to "Chainlink"
    )

    init {
        viewModelScope.launch {
            auth.authStateChanged.collect { user ->
                if (user != null) {
                    loadAccounts()
                    loadUserSettings()
                } else {
                    _uiState.value = AccountsUiState.Error("Utilisateur non connecté.")
                }
            }
        }
        fetchCryptoRates()
        fetchLivretARate()
    }

    fun fetchLivretARate() {
        viewModelScope.launch {
            try {
                val response: HttpResponse = httpClient.get("https://www.data.gouv.fr/fr/datasets/r/f01b058a-36b1-4b71-b0e6-9b7e7c7e39a3")
                val csvText = response.bodyAsText()
                val lines = csvText.split("\n")
                if (lines.size > 1) {
                    val dataLine = lines.filter { it.isNotBlank() }.last()
                    val columns = dataLine.split(";")
                    _livretARate.value = columns[1].replace(",", ".").toDoubleOrNull() ?: 3.0
                }
            } catch (e: Exception) {
                _livretARate.value = 3.0
            }
        }
    }

    private val coinIdMap = mapOf(
        "BTC" to "bitcoin", "ETH" to "ethereum", "SOL" to "solana",
        "XRP" to "ripple", "ADA" to "cardano", "DOT" to "polkadot",
        "DOGE" to "dogecoin", "AVAX" to "avalanche", "MATIC" to "matic-network",
        "LINK" to "chainlink", "USDT" to "tether"
    )

    fun fetchCryptoRates() {
        viewModelScope.launch {
            try {
                val ids = coinIdMap.values.joinToString(",")
                val response: HttpResponse = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=eur")
                val jsonString = response.bodyAsText()
                val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
                
                val map = mutableMapOf<String, Double>()
                coinIdMap.forEach { (symbol, id) ->
                    jsonObject[id]?.jsonObject?.get("eur")?.jsonPrimitive?.double?.let {
                        map[symbol] = it
                    }
                }
                _cryptoRates.value = map
            } catch (e: Exception) { }
        }
    }

    fun loadAccounts() {
        val currentUser = auth.currentUser ?: return
        _uiState.value = AccountsUiState.Loading

        viewModelScope.launch {
            firestore.collection("accounts")
                .where { "members" contains currentUser.uid }
                .snapshots().collect { snapshot ->
                    val accountsList = snapshot.documents.map { doc ->
                        doc.data<Account>().copy(id = doc.id)
                    }.sortedBy { it.order }
                    lastAccountsList = accountsList
                    updateTransactionsListeners(accountsList)
                }
        }
    }

    private fun updateTransactionsListeners(accounts: List<Account>) {
        val currentAccountIds = accounts.map { it.id }.toSet()
        transactionsListeners.keys.filter { it !in currentAccountIds }.forEach { id ->
            transactionsListeners[id]?.cancel()
            transactionsListeners.remove(id)
            accountTransactionsMap.remove(id)
        }

        if (accounts.isEmpty()) {
            _uiState.value = AccountsUiState.Success(emptyList(), emptyMap())
            return
        }

        for (account in accounts) {
            if (!transactionsListeners.containsKey(account.id)) {
                transactionsListeners[account.id] = viewModelScope.launch {
                    firestore.collection("accounts")
                        .document(account.id)
                        .collection("transactions")
                        .orderBy("date", Direction.DESCENDING)
                        .snapshots().collect { txSnapshot ->
                            val txList = txSnapshot.documents.map { doc ->
                                doc.data<Transaction>().copy(id = doc.id)
                            }
                            accountTransactionsMap[account.id] = txList
                            recalculateBalances(lastAccountsList)
                        }
                }
            }
        }
        recalculateBalances(accounts)
    }

    private fun recalculateBalances(accounts: List<Account>) {
        viewModelScope.launch {
            val balancesMap = accounts.associate { account ->
                val txs = accountTransactionsMap[account.id] ?: emptyList()
                account.id to calculateCurrentRealBalance(account, txs)
            }
            
            val debtsMap = accounts.filter { it.type == "CREDIT" }.associate { account ->
                val txs = accountTransactionsMap[account.id] ?: emptyList()
                val now = DateTimeUtils.now().toEpochMilliseconds()
                val pastTxs = txs.filter { it.type == "INCOME" && it.date.toEpochMilliseconds() <= now }
                val lastTx = pastTxs.minByOrNull { now - it.date.toEpochMilliseconds() }
                
                val remainingValue = lastTx?.remainingDebt ?: run {
                    val initialCapital = account.totalAmount ?: 0.0
                    val totalPrincipalRepaid = pastTxs.sumOf { it.principalPart ?: 0.0 }
                    initialCapital - totalPrincipalRepaid
                }
                account.id to (remainingValue ?: 0.0)
            }
            _uiState.value = AccountsUiState.Success(accounts, balancesMap, debtsMap)
        }
    }

    private fun calculateCurrentRealBalance(account: Account, transactions: List<Transaction>): Double {
        var total = account.initialBalance
        val now = DateTimeUtils.now()
        val currentYear = DateTimeUtils.getYear(now)
        val currentMonth = DateTimeUtils.getMonth(now)
        val targetIndex = currentYear * 12 + currentMonth
        
        val isCrypto = account.type == "CRYPTO"

        transactions.forEach { tx ->
            val txInstant = tx.date.toInstant()
            val startIndex = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)

            if (tx.isRecurring) {
                val endIndex = tx.endDate?.let {
                    val endInstant = it.toInstant()
                    DateTimeUtils.getYear(endInstant) * 12 + DateTimeUtils.getMonth(endInstant)
                } ?: Int.MAX_VALUE
                val effectiveEndIndex = minOf(targetIndex, endIndex - 1)

                if (startIndex <= effectiveEndIndex) {
                    if (isCrypto) {
                        val count = (effectiveEndIndex - startIndex + 1).coerceAtLeast(0)
                        total += (count * tx.amount)
                    } else {
                        total += tx.checkedMonths.count { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                val mIndex = y * 12 + m
                                mIndex in startIndex..effectiveEndIndex
                            } catch (e: Exception) { false }
                        } * tx.amount
                    }
                }
            } else {
                if (startIndex <= targetIndex) {
                    if (isCrypto) {
                        total += tx.amount
                    } else {
                        val mKey = "${DateTimeUtils.getYear(txInstant)}-${(DateTimeUtils.getMonth(txInstant) + 1).toString().padStart(2, '0')}"
                        if (tx.isCheckedForMonth(mKey)) total += tx.amount
                    }
                }
            }
        }
        return total
    }

    private fun loadUserSettings() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            firestore.collection("users").document(userId).snapshots().collect { snapshot ->
                if (snapshot.exists) {
                    try {
                        val currency = snapshot.get<String>("currency")
                        if (currency.isNotBlank()) {
                            _appCurrency.value = currency
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun addAccount(account: Account, onAccountCreated: (String) -> Unit = {}) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val newAccountRef = firestore.collection("accounts").document("") // Empty string for auto-id
                val finalAccount = account.copy(id = newAccountRef.id, ownerId = currentUser.uid, members = listOf(currentUser.uid))
                newAccountRef.set(finalAccount)
                onAccountCreated(finalAccount.id)
            } catch (e: Exception) {
                getPlatform().log("ADD_ACCOUNT", "Erreur: ${e.message}")
            }
        }
    }

    fun updateAccount(account: Account, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("accounts").document(account.id).set(account)
                onSuccess()
            } catch (e: Exception) { }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val accountRef = firestore.collection("accounts").document(accountId)
                val accountDoc = accountRef.get()
                val type = accountDoc.get<String>("type")
                val linkedId = accountDoc.get<String>("linkedAccountId")

                if (type == "CREDIT" && !linkedId.isNullOrBlank()) {
                    val linkedTransactions = firestore.collection("accounts")
                        .document(linkedId)
                        .collection("transactions")
                        .where { "targetAccountId" equalTo accountId }
                        .get()
                    
                    linkedTransactions.documents.forEach { doc ->
                        doc.reference.delete()
                    }
                }

                val selfTransactions = accountRef.collection("transactions").get()
                selfTransactions.documents.forEach { doc -> doc.reference.delete() }
                accountRef.delete()
            } catch (e: Exception) { }
        }
    }

    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val userQuery = firestore.collection("users").where { "email" equalTo memberEmail }.get()
                if (userQuery.documents.isEmpty()) {
                    onResult(false, "Aucun utilisateur trouvé avec cet e-mail.")
                    return@launch
                }
                val newUserId = userQuery.documents.first().id
                val accountRef = firestore.collection("accounts").document(accountId)
                val snapshot = accountRef.get()
                val currentMembers = try { snapshot.get<List<String>>("members") } catch (e: Exception) { emptyList() }
                if (!currentMembers.contains(newUserId)) {
                    val updatedMembers = currentMembers + newUserId
                    accountRef.update("members" to updatedMembers, "isJoint" to true)
                }
                onResult(true, "Membre ajouté avec succès !")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erreur")
            }
        }
    }

    fun updateAccountsOrder(orderedAccounts: List<Account>) {
        viewModelScope.launch {
            try {
                orderedAccounts.forEachIndexed { index, account ->
                    firestore.collection("accounts").document(account.id).update("order" to index)
                }
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transactionsListeners.values.forEach { it.cancel() }
    }
}

fun Timestamp.toInstant(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.fromEpochMilliseconds(toEpochMilliseconds())
fun Timestamp.toEpochMilliseconds(): Long = (seconds * 1000) + (nanoseconds / 1000000)
