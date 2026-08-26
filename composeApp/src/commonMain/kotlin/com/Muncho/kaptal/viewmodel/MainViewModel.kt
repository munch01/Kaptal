package com.muncho.kaptal.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.network.httpClient
import com.muncho.kaptal.utils.DateTimeUtils
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.datetime.*
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

    private val _cryptoHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val cryptoHistory: StateFlow<List<Pair<Long, Double>>> = _cryptoHistory.asStateFlow()

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

    fun fetchCryptoHistory(symbol: String) {
        val coinId = coinIdMap[symbol] ?: return
        viewModelScope.launch {
            try {
                val response: HttpResponse = httpClient.get("https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=eur&days=30&interval=daily")
                val jsonString = response.bodyAsText()
                val prices = Json.parseToJsonElement(jsonString).jsonObject["prices"]?.jsonArray
                val list = mutableListOf<Pair<Long, Double>>()
                prices?.forEach { item ->
                    val array = item.jsonArray
                    list.add(array[0].jsonPrimitive.double.toLong() to array[1].jsonPrimitive.double)
                }
                _cryptoHistory.value = list
            } catch (e: Exception) { }
        }
    }

    fun loadAccounts() {
        val currentUser = auth.currentUser ?: return
        _uiState.value = AccountsUiState.Loading

        viewModelScope.launch {
            firestore.collection("accounts")
                .where("members", arrayContains = currentUser.uid)
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

    // --- LOGIQUE INTÉRÊTS LIVRET A ---
    fun calculateLivretAInterests(
        account: Account, 
        transactions: List<Transaction>, 
        rate: Double,
        targetYear: Int? = null,
        targetMonth: Int? = null,
        onlyChecked: Boolean = true
    ): Double {
        val now = DateTimeUtils.now()
        val currentYear = targetYear ?: DateTimeUtils.getYear(now)
        
        var totalInterests = 0.0
        val ratePerFortnight = (rate / 100.0) / 24.0

        val targetQuinzaineLimit = if (targetYear != null && targetMonth != null) {
            (targetMonth + 1) * 2
        } else 24

        for (q in 0 until targetQuinzaineLimit) {
            val month = q / 2
            val isSecondHalf = q % 2 == 1
            
            val balance = calculateBalanceAtFortnight(account, transactions, currentYear, month, isSecondHalf, onlyChecked)
            
            if (balance > 0) {
                val fortnightDate = LocalDateTime(currentYear, month + 1, if (isSecondHalf) 16 else 1, 0, 0)
                    .toInstant(TimeZone.currentSystemDefault())
                
                if (!onlyChecked || fortnightDate < now) {
                    totalInterests += balance * ratePerFortnight
                }
            }
        }
        return totalInterests
    }

    private fun calculateBalanceAtFortnight(
        account: Account,
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        isSecondHalf: Boolean,
        onlyChecked: Boolean
    ): Double {
        var balance = account.initialBalance
        val pivotInstant = LocalDateTime(year, month + 1, if (isSecondHalf) 16 else 1, 0, 0)
            .toInstant(TimeZone.currentSystemDefault())

        for (tx in transactions) {
            val txInstant = tx.date.toInstant()
            if (tx.isRecurring) {
                var iterInstant = txInstant
                val endInstant = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                while (iterInstant < pivotInstant && iterInstant < endInstant) {
                    val mKey = "${DateTimeUtils.getYear(iterInstant)}-${(DateTimeUtils.getMonth(iterInstant) + 1).toString().padStart(2, '0')}"
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        val valInstant = getValueDate(iterInstant, tx.amount)
                        if (valInstant <= pivotInstant) balance += tx.amount
                    }
                    iterInstant = DateTimeUtils.addMonths(iterInstant, 1)
                }
            } else {
                val mKey = "${DateTimeUtils.getYear(txInstant)}-${(DateTimeUtils.getMonth(txInstant) + 1).toString().padStart(2, '0')}"
                if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                    val valInstant = getValueDate(txInstant, tx.amount)
                    if (valInstant <= pivotInstant) balance += tx.amount
                }
            }
        }
        return balance
    }

    private fun getValueDate(instant: Instant, amount: Double): Instant {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return if (amount > 0) { // Dépôt
            if (dt.dayOfMonth <= 15) {
                LocalDateTime(dt.year, dt.monthNumber, 16, 0, 0).toInstant(TimeZone.currentSystemDefault())
            } else {
                val nextMonth = instant.plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
                val nextMonthDt = nextMonth.toLocalDateTime(TimeZone.currentSystemDefault())
                LocalDateTime(nextMonthDt.year, nextMonthDt.monthNumber, 1, 0, 0).toInstant(TimeZone.currentSystemDefault())
            }
        } else { // Retrait
            if (dt.dayOfMonth <= 15) {
                LocalDateTime(dt.year, dt.monthNumber, 1, 0, 0).toInstant(TimeZone.currentSystemDefault())
            } else {
                LocalDateTime(dt.year, dt.monthNumber, 16, 0, 0).toInstant(TimeZone.currentSystemDefault())
            }
        }
    }

    fun calculateDailyInterests(
        account: Account, 
        transactions: List<Transaction>, 
        rate: Double,
        targetYear: Int? = null,
        targetMonth: Int? = null,
        onlyChecked: Boolean = true
    ): Double {
        val now = DateTimeUtils.now()
        val year = targetYear ?: DateTimeUtils.getYear(now)
        var totalInterests = 0.0
        val dailyRate = (rate / 100.0) / 365.0
        
        var iterInstant = DateTimeUtils.startOfYear(year)
        val limitInstant = if (targetYear != null && targetMonth != null) {
            DateTimeUtils.endOfMonth(targetYear, targetMonth)
        } else now

        while (iterInstant < limitInstant && iterInstant < now) {
            val balance = calculateBalanceAtDate(account, transactions, iterInstant, onlyChecked)
            if (balance > 0) totalInterests += balance * dailyRate
            iterInstant = DateTimeUtils.addDays(iterInstant, 1)
        }
        return totalInterests
    }

    fun calculateBalanceAtDate(account: Account, transactions: List<Transaction>, instant: Instant, onlyChecked: Boolean): Double {
        var balance = account.initialBalance
        transactions.forEach { tx ->
            val txInstant = tx.date.toInstant()
            if (tx.isRecurring) {
                var iter = txInstant
                val end = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                while (iter <= instant && iter < end) {
                    val mKey = "${DateTimeUtils.getYear(iter)}-${(DateTimeUtils.getMonth(iter) + 1).toString().padStart(2, '0')}"
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) balance += tx.amount
                    iter = DateTimeUtils.addMonths(iter, 1)
                }
            } else {
                if (txInstant <= instant) {
                    val mKey = "${DateTimeUtils.getYear(txInstant)}-${(DateTimeUtils.getMonth(txInstant) + 1).toString().padStart(2, '0')}"
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) balance += tx.amount
                }
            }
        }
        return balance
    }

    fun calculateProjections(account: Account, transactions: List<Transaction>, rate: Double): List<Pair<Long, Double>> {
        val list = mutableListOf<Pair<Long, Double>>()
        var currentInstant = DateTimeUtils.now()
        var currentProjectedBalance = calculateCurrentRealBalance(account, transactions)
        val monthlyRate = (rate / 100.0) / 12.0
        
        list.add(currentInstant.toEpochMilliseconds() to currentProjectedBalance)
        
        for (i in 1..12) {
            currentInstant = DateTimeUtils.addMonths(currentInstant, 1)
            currentProjectedBalance += (currentProjectedBalance * monthlyRate)
            
            val dt = currentInstant.toLocalDateTime(TimeZone.currentSystemDefault())
            transactions.filter { it.isRecurring }.forEach { tx ->
                val txI = tx.date.toInstant()
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                if (currentInstant >= txI && currentInstant < endI) {
                    currentProjectedBalance += tx.amount
                }
            }
            list.add(currentInstant.toEpochMilliseconds() to currentProjectedBalance)
        }
        return list
    }

    fun calculateTotalInvestment(account: Account, transactions: List<Transaction>, referencePrice: Double = 0.0): Double {
        var total = account.initialInvestmentEur ?: (account.initialBalance * referencePrice)
        val now = DateTimeUtils.now()
        val currentMonthIndex = DateTimeUtils.getYear(now) * 12 + DateTimeUtils.getMonth(now)

        transactions.forEach { tx ->
            val fiatFlow = tx.investmentEur ?: 0.0
            if (fiatFlow == 0.0) return@forEach
            
            val isMoneyIn = tx.type == "INCOME" || (tx.type == "TRANSFER" && tx.amount > 0)
            val factor = if (isMoneyIn) 1.0 else -1.0

            val txInstant = tx.date.toInstant()
            val startIndex = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)

            if (tx.isRecurring) {
                val endIndex = tx.endDate?.let {
                    val endI = it.toInstant()
                    DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)
                } ?: Int.MAX_VALUE
                val effectiveEndIndex = minOf(currentMonthIndex, endIndex - 1)
                if (startIndex <= effectiveEndIndex) {
                    val count = (effectiveEndIndex - startIndex + 1).coerceAtLeast(0)
                    total += (fiatFlow * factor * count)
                }
            } else {
                if (startIndex <= currentMonthIndex) {
                    total += (fiatFlow * factor)
                }
            }
        }
        return total
    }

    fun calculatePortfolioPerformance(account: Account, transactions: List<Transaction>, currentRate: Double, referencePrice: Double = 0.0): Pair<Double, Double> {
        val totalInvested = calculateTotalInvestment(account, transactions, referencePrice)
        val currentQuantity = calculateCurrentRealBalance(account, transactions)
        val currentValue = currentQuantity * currentRate
        
        val gainLoss = currentValue - totalInvested
        val percentage = if (totalInvested > 0) (gainLoss / totalInvested) * 100 else 0.0
        
        return Pair(gainLoss, percentage)
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
                val newId = generateRandomId()
                val finalAccount = account.copy(id = newId, ownerId = currentUser.uid, members = listOf(currentUser.uid))
                firestore.collection("accounts").document(newId).set(finalAccount)
                onAccountCreated(newId)
            } catch (e: Exception) {
                getPlatform().log("ADD_ACCOUNT", "Erreur: ${e.message}")
            }
        }
    }

    private fun generateRandomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
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
