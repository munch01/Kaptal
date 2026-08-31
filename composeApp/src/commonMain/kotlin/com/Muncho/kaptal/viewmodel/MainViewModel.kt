package com.muncho.kaptal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.network.httpClient
import com.muncho.kaptal.utils.DateTimeUtils
import com.muncho.kaptal.utils.ExportUtils
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
    private val mapMutex = Mutex()
    private var lastAccountsList: List<Account> = emptyList()

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

    private val coinIdMap = mapOf(
        "BTC" to "bitcoin", "ETH" to "ethereum", "SOL" to "solana",
        "XRP" to "ripple", "ADA" to "cardano", "DOT" to "polkadot",
        "DOGE" to "dogecoin", "AVAX" to "avalanche", "MATIC" to "matic-network",
        "LINK" to "chainlink", "USDT" to "tether", "BNB" to "binancecoin",
        "SHIB" to "shiba-inu", "DAI" to "dai", "LTC" to "litecoin",
        "PEPE" to "pepe", "NEAR" to "near"
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

    fun fetchCryptoRates() {
        viewModelScope.launch {
            try {
                val ids = coinIdMap.values.joinToString(",")
                val response: HttpResponse = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=eur")
                val jsonString = response.bodyAsText()
                getPlatform().log("CRYPTO_DEBUG", "Coingecko response: $jsonString")
                val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
                
                val map = mutableMapOf<String, Double>()
                coinIdMap.forEach { (symbol, id) ->
                    val price = jsonObject[id]?.jsonObject?.get("eur")?.jsonPrimitive?.double
                    if (price != null) {
                        map[symbol.uppercase()] = price
                    } else {
                        getPlatform().log("CRYPTO_DEBUG", "Pas de prix trouvé pour $symbol ($id)")
                    }
                }
                _cryptoRates.value = map
                getPlatform().log("CRYPTO_DEBUG", "Rates updated: ${map.keys.joinToString(", ")}")
            } catch (e: Exception) {
                getPlatform().log("CRYPTO_ERROR", "Rates: ${e.message}")
            }
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
            } catch (e: Exception) {
                getPlatform().log("CRYPTO_ERROR", "Rates: ${e.message}")
            }
        }
    }

    fun loadAccounts() {
        val currentUser = auth.currentUser ?: return
        _uiState.value = AccountsUiState.Loading

        viewModelScope.launch {
            firestore.collection("accounts")
                .where { "members" contains currentUser.uid }
                .snapshots().collect { snapshot ->
                    val accountsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.data<Account>().copy(id = doc.id)
                        } catch (e: Exception) {
                            getPlatform().log("FIRESTORE_ERROR", "Erreur désérialisation compte ${doc.id}: ${e.message}")
                            null
                        }
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
                            mapMutex.withLock {
                                accountTransactionsMap[account.id] = txList
                            }
                            recalculateBalances(lastAccountsList)
                        }
                }
            }
        }
        recalculateBalances(accounts)
    }

    private fun recalculateBalances(accounts: List<Account>) {
        viewModelScope.launch {
            val balancesMap = mapMutex.withLock {
                accounts.associate { account ->
                    val txs = accountTransactionsMap[account.id] ?: emptyList()
                    account.id to calculateCurrentRealBalance(account, txs)
                }
            }
            
            val debtsMap = mapMutex.withLock {
                accounts.filter { it.type == "CREDIT" }.associate { account ->
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
            }
            _uiState.value = AccountsUiState.Success(accounts, balancesMap, debtsMap)
        }
    }

    private fun calculateCurrentRealBalance(account: Account, transactions: List<Transaction>): Double {
        var total = account.initialBalance
        val now = DateTimeUtils.now()
        val isCrypto = account.type == "CRYPTO"

        if (isCrypto) {
            transactions.forEach { tx ->
                val txInstant = tx.date.toInstant()
                if (tx.isRecurring) {
                    val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                    val stepVal = when {
                        tx.recurrenceInterval == "QUARTERLY" -> 3
                        tx.recurrenceInterval == "ANNUAL" -> 12
                        tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                            tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                        }
                        else -> 1
                    }
                    val effectiveStep = if (stepVal < 1) 1 else stepVal
                    
                    var occurrence = txInstant
                    var count = 0
                    while (occurrence <= now && occurrence < endI) {
                        total += tx.amount
                        count++
                        occurrence = DateTimeUtils.addMonths(txInstant, count * effectiveStep)
                    }
                } else {
                    if (txInstant <= now) {
                        total += tx.amount
                    }
                }
            }
            return total
        }

        val targetMonthTotal = DateTimeUtils.getYear(now) * 12 + DateTimeUtils.getMonth(now)
        transactions.forEach { tx ->
            val txInstant = tx.date.toInstant()

            if (tx.isRecurring) {
                val startMonthTotal = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                val endMonthTotal = DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)
                
                val stepVal = when {
                    tx.recurrenceInterval == "QUARTERLY" -> 3
                    tx.recurrenceInterval == "ANNUAL" -> 12
                    tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                        tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                    }
                    else -> 1
                }
                val effectiveStep = if (stepVal < 1) 1 else stepVal

                for (iterMonthTotal in startMonthTotal..targetMonthTotal step effectiveStep) {
                    if (iterMonthTotal > endMonthTotal) break
                    
                    val iterYear = iterMonthTotal / 12
                    val iterMonth = iterMonthTotal % 12
                    val mKey = "${iterYear}-${(iterMonth + 1).toString().padStart(2, '0')}"
                    if (tx.isCheckedForMonth(mKey)) total += tx.amount
                }
            } else {
                val txMonthTotal = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)
                if (txMonthTotal <= targetMonthTotal) {
                    val mKey = "${DateTimeUtils.getYear(txInstant)}-${(DateTimeUtils.getMonth(txInstant) + 1).toString().padStart(2, '0')}"
                    if (tx.isCheckedForMonth(mKey)) total += tx.amount
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
                val fortnightDate = LocalDateTime(currentYear, month + 1, if (isSecondHalf) 16 else 1, 12, 0, 0)
                    .toInstant(TimeZone.UTC)
                
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
        val pivotInstant = LocalDateTime(year, month + 1, if (isSecondHalf) 16 else 1, 12, 0, 0)
            .toInstant(TimeZone.UTC)

        val pivotTotal = year * 12 + month

        for (tx in transactions) {
            val txInstant = tx.date.toInstant()
            val txYear = DateTimeUtils.getYear(txInstant)
            val txMonth = DateTimeUtils.getMonth(txInstant)
            val startIndex = txYear * 12 + txMonth

            if (tx.isRecurring) {
                val startTotal = txYear * 12 + txMonth
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                val endTotal = DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)

                val stepVal = when {
                    tx.recurrenceInterval == "QUARTERLY" -> 3
                    tx.recurrenceInterval == "ANNUAL" -> 12
                    tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                        tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                    }
                    else -> 1
                }
                val effectiveStep = if (stepVal < 1) 1 else stepVal

                for (iterTotal in startTotal..pivotTotal step effectiveStep) {
                    if (iterTotal > endTotal) break
                    
                    val iterYear = iterTotal / 12
                    val iterMonth = iterTotal % 12
                    val mKey = "$iterYear-${(iterMonth + 1).toString().padStart(2, '0')}"
                    
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        val iterInstant = DateTimeUtils.addMonths(txInstant, iterTotal - startTotal)
                        val valInstant = getValueDate(iterInstant, tx.amount)
                        if (valInstant <= pivotInstant) balance += tx.amount
                    }
                }
            } else {
                val mKey = "$txYear-${(txMonth + 1).toString().padStart(2, '0')}"
                if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                    val valInstant = getValueDate(txInstant, tx.amount)
                    if (valInstant <= pivotInstant) balance += tx.amount
                }
            }
        }
        return balance
    }

    private fun getValueDate(instant: Instant, amount: Double): Instant {
        val dt = instant.toLocalDateTime(TimeZone.UTC)
        return if (amount > 0) { // Dépôt
            if (dt.dayOfMonth <= 15) {
                LocalDateTime(dt.year, dt.monthNumber, 16, 12, 0, 0).toInstant(TimeZone.UTC)
            } else {
                val nextMonth = instant.plus(1, DateTimeUnit.MONTH, TimeZone.UTC)
                val nextMonthDt = nextMonth.toLocalDateTime(TimeZone.UTC)
                LocalDateTime(nextMonthDt.year, nextMonthDt.monthNumber, 1, 12, 0, 0).toInstant(TimeZone.UTC)
            }
        } else { // Retrait
            if (dt.dayOfMonth <= 15) {
                LocalDateTime(dt.year, dt.monthNumber, 1, 12, 0, 0).toInstant(TimeZone.UTC)
            } else {
                LocalDateTime(dt.year, dt.monthNumber, 16, 12, 0, 0).toInstant(TimeZone.UTC)
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
        val targetTotal = DateTimeUtils.getYear(instant) * 12 + DateTimeUtils.getMonth(instant)

        transactions.forEach { tx ->
            val txInstant = tx.date.toInstant()
            val txYear = DateTimeUtils.getYear(txInstant)
            val txMonth = DateTimeUtils.getMonth(txInstant)

            if (tx.isRecurring) {
                val startTotal = txYear * 12 + txMonth
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                val endTotal = DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)

                val stepVal = when {
                    tx.recurrenceInterval == "QUARTERLY" -> 3
                    tx.recurrenceInterval == "ANNUAL" -> 12
                    tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                        val value = tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                        if (value < 1) 1 else value
                    }
                    else -> 1
                }
                val effectiveStep = if (stepVal < 1) 1 else stepVal

                for (iterTotal in startTotal..targetTotal step effectiveStep) {
                    if (iterTotal > endTotal) break
                    
                    val iterYear = iterTotal / 12
                    val iterMonth = iterTotal % 12
                    val mKey = "$iterYear-${(iterMonth + 1).toString().padStart(2, '0')}"
                    
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        val iterInstant = DateTimeUtils.addMonths(txInstant, iterTotal - startTotal)
                        if (iterInstant <= instant) balance += tx.amount
                    }
                }
            } else {
                if (txInstant <= instant) {
                    val mKey = "$txYear-${(txMonth + 1).toString().padStart(2, '0')}"
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) balance += tx.amount
                }
            }
        }
        return balance
    }

    fun calculateTotalInvestment(account: Account, transactions: List<Transaction>, referencePrice: Double = 0.0): Double {
        val effectiveRefPrice = if (referencePrice <= 0.0) {
            val symbol = account.cryptoSymbol ?: "BTC"
            _cryptoRates.value[symbol] ?: 0.0
        } else referencePrice

        // Si initialInvestmentEur est nul, on l'estime avec le prix de référence
        var total = account.initialInvestmentEur ?: (account.initialBalance * effectiveRefPrice)
        val now = DateTimeUtils.now()
        val targetMonthTotal = DateTimeUtils.getYear(now) * 12 + DateTimeUtils.getMonth(now)

        transactions.forEach { tx ->
            // Si l'utilisateur n'a pas mis d'investissement en EUR, on estime via le prix de référence
            val fiatFlow = tx.investmentEur ?: (abs(tx.amount) * effectiveRefPrice)
            if (fiatFlow == 0.0) return@forEach
            
            val isMoneyIn = tx.type == "INCOME" || (tx.type == "TRANSFER" && tx.amount > 0)
            val factor = if (isMoneyIn) 1.0 else -1.0

            val txInstant = tx.date.toInstant()

            if (tx.isRecurring) {
                val startMonthTotal = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                val endMonthTotal = DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)
                
                val stepVal = when {
                    tx.recurrenceInterval == "QUARTERLY" -> 3
                    tx.recurrenceInterval == "ANNUAL" -> 12
                    tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                        tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                    }
                    else -> 1
                }
                val effectiveStep = if (stepVal < 1) 1 else stepVal

                for (iterMonthTotal in startMonthTotal..targetMonthTotal step effectiveStep) {
                    if (iterMonthTotal > endMonthTotal) break
                    total += (fiatFlow * factor)
                }
            } else {
                val txMonthTotal = DateTimeUtils.getYear(txInstant) * 12 + DateTimeUtils.getMonth(txInstant)
                if (txMonthTotal <= targetMonthTotal) {
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
                        val currency = snapshot.get<String?>("currency")
                        if (currency != null && currency.isNotBlank()) {
                            _appCurrency.value = currency
                        }
                    } catch (e: Exception) {
                getPlatform().log("CRYPTO_ERROR", "History: ${e.message}")
            }
                }
            }
        }
    }

    fun addAccount(account: Account, memberEmail: String? = null, onComplete: () -> Unit = {}) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val newId = generateRandomId()
                val finalAccount = account.copy(
                    id = newId,
                    ownerId = currentUser.uid,
                    members = listOf(currentUser.uid)
                )
                
                firestore.collection("accounts").document(newId).set(finalAccount)
                
                // Navigation sécurisée sur le Main Thread
                viewModelScope.launch(Dispatchers.Main) {
                    if (!memberEmail.isNullOrBlank()) {
                        addMemberToAccount(newId, memberEmail) { _, _ -> 
                            onComplete() 
                        }
                    } else {
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                getPlatform().log("ADD_ACCOUNT_ERROR", "Erreur: ${e.message}")
                viewModelScope.launch(Dispatchers.Main) {
                    getPlatform().showToast("Erreur lors de la création")
                    onComplete()
                }
            }
        }
    }

    private fun generateRandomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    fun updateAccount(account: Account, onSuccess: () -> Unit) {
        if (account.id.isBlank()) {
            getPlatform().log("UPDATE_ACCOUNT_ERROR", "ID de compte vide")
            getPlatform().showToast("Erreur : ID manquant")
            return
        }
        viewModelScope.launch {
            try {
                getPlatform().log("UPDATE_ACCOUNT", "Tentative de mise à jour pour ID: ${account.id}")
                firestore.collection("accounts").document(account.id).set(account)
                getPlatform().log("UPDATE_ACCOUNT", "Mise à jour Firestore réussie")
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                getPlatform().log("UPDATE_ACCOUNT_ERROR", "Crash pendant l'update: ${e.message}")
                getPlatform().showToast("Erreur lors de l'enregistrement")
                // On appelle onSuccess quand même pour débloquer l'UI ? 
                // Non, on laisse l'utilisateur réessayer ou on ferme
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess()
                }
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                getPlatform().log("DELETE_ACCOUNT", "Début suppression compte: $accountId")
                val accountRef = firestore.collection("accounts").document(accountId)
                val accountSnapshot = accountRef.get()
                
                if (!accountSnapshot.exists) {
                    getPlatform().log("DELETE_ACCOUNT_ERROR", "Compte $accountId n'existe pas")
                    return@launch
                }

                // Utilisation de try-catch pour les champs qui pourraient être manquants
                val type = try { accountSnapshot.get<String?>("type") } catch (e: Exception) { null }
                val linkedId = try { accountSnapshot.get<String?>("linkedAccountId") } catch (e: Exception) { null }

                getPlatform().log("DELETE_ACCOUNT", "Type: $type, LinkedId: $linkedId")

                // 1. Supprimer les transactions miroirs (si c'est un crédit lié)
                if (type == "CREDIT" && !linkedId.isNullOrBlank()) {
                    getPlatform().log("DELETE_ACCOUNT", "Suppression des transactions liées dans le compte $linkedId")
                    try {
                        val linkedTransactions = firestore.collection("accounts")
                            .document(linkedId)
                            .collection("transactions")
                            .where { "targetAccountId" equalTo accountId }
                            .get()
                        
                        for (doc in linkedTransactions.documents) {
                            getPlatform().log("DELETE_ACCOUNT", "Suppression tx miroir: ${doc.id}")
                            doc.reference.delete()
                        }
                    } catch (e: Exception) {
                        getPlatform().log("DELETE_ACCOUNT_ERROR", "Erreur tx miroirs: ${e.message}")
                    }
                }

                // 2. Supprimer toutes les transactions du compte lui-même
                getPlatform().log("DELETE_ACCOUNT", "Suppression des transactions du compte $accountId")
                try {
                    val selfTransactions = accountRef.collection("transactions").get()
                    for (doc in selfTransactions.documents) {
                        getPlatform().log("DELETE_ACCOUNT", "Suppression tx: ${doc.id}")
                        doc.reference.delete()
                    }
                } catch (e: Exception) {
                    getPlatform().log("DELETE_ACCOUNT_ERROR", "Erreur tx propres: ${e.message}")
                }

                // 3. Enfin, supprimer le document du compte
                accountRef.delete()
                getPlatform().log("DELETE_ACCOUNT", "Compte $accountId supprimé avec succès")
                getPlatform().showToast("Compte supprimé")
            } catch (e: Exception) {
                getPlatform().log("DELETE_ACCOUNT_ERROR", "Erreur globale suppression: ${e.message}")
                getPlatform().showToast("Erreur lors de la suppression")
            }
        }
    }

    fun addMemberToAccount(accountId: String, memberEmail: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanEmail = memberEmail.trim().lowercase()
                getPlatform().log("SHARE_DEBUG", "Tentative de partage du compte $accountId avec $cleanEmail")
                
                val userQuery = firestore.collection("users").where { "email" equalTo cleanEmail }.get()
                if (userQuery.documents.isEmpty()) {
                    getPlatform().log("SHARE_ERROR", "Aucun utilisateur trouvé pour l'email: $cleanEmail")
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(false, "Aucun utilisateur trouvé avec cet e-mail. Il doit d'abord se connecter une fois à l'app.")
                    }
                    return@launch
                }
                val newUserDoc = userQuery.documents.first()
                val newUserId = newUserDoc.id
                getPlatform().log("SHARE_DEBUG", "Utilisateur trouvé. UID: $newUserId")

                val accountRef = firestore.collection("accounts").document(accountId)
                val snapshot = accountRef.get()
                if (!snapshot.exists) {
                    getPlatform().log("SHARE_ERROR", "Compte $accountId introuvable dans Firestore")
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(false, "Compte introuvable.")
                    }
                    return@launch
                }

                val currentMembers = try { 
                    snapshot.get<List<String>?>("members") ?: emptyList()
                } catch (e: Exception) { 
                    getPlatform().log("SHARE_ERROR", "Erreur lors de la lecture des membres: ${e.message}")
                    emptyList() 
                }

                if (!currentMembers.contains(newUserId)) {
                    val updatedMembers = currentMembers + newUserId
                    accountRef.update("members" to updatedMembers, "isJoint" to true)
                    getPlatform().log("SHARE_DEBUG", "Membres mis à jour avec succès pour le compte $accountId")
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(true, "Membre ajouté avec succès !")
                    }
                } else {
                    getPlatform().log("SHARE_DEBUG", "L'utilisateur $newUserId a déjà accès au compte $accountId")
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(true, "L'utilisateur a déjà accès à ce compte.")
                    }
                }
            } catch (e: Exception) {
                getPlatform().log("SHARE_ERROR", "Erreur lors du partage: ${e.message}")
                val msg = e.message ?: "Erreur inconnue"
                viewModelScope.launch(Dispatchers.Main) {
                    onResult(false, msg)
                }
            }
        }
    }

    fun updateAccountsOrder(orderedAccounts: List<Account>) {
        viewModelScope.launch {
            try {
                orderedAccounts.forEachIndexed { index, account ->
                    firestore.collection("accounts").document(account.id).update("order" to index)
                }
            } catch (e: Exception) {
                getPlatform().log("CRYPTO_ERROR", "Rates: ${e.message}")
            }
        }
    }

    fun exportAccountData(account: Account, format: String) {
        viewModelScope.launch {
            val transactions = accountTransactionsMap[account.id] ?: emptyList()
            val content = if (format == "CSV") {
                ExportUtils.generateTransactionsCsv(account, transactions)
            } else {
                ExportUtils.generateAccountJson(account, transactions)
            }
            val fileName = "Kaptal_${account.name.replace(" ", "_")}.${format.lowercase()}"
            val mimeType = if (format == "CSV") "text/csv" else "application/json"
            getPlatform().shareFile(content, fileName, mimeType)
        }
    }

    fun exportAllAccountsData(format: String) {
        viewModelScope.launch {
            val accounts = lastAccountsList
            if (accounts.isEmpty()) {
                getPlatform().showToast("Aucun compte à exporter")
                return@launch
            }

            accounts.forEach { account ->
                val transactions = accountTransactionsMap[account.id] ?: emptyList()
                val content = if (format == "CSV") {
                    ExportUtils.generateTransactionsCsv(account, transactions)
                } else {
                    ExportUtils.generateAccountJson(account, transactions)
                }
                val fileName = "Kaptal_${account.name.replace(" ", "_")}.${format.lowercase()}"
                val mimeType = if (format == "CSV") "text/csv" else "application/json"
                getPlatform().shareFile(content, fileName, mimeType)
                // Note: Multiple shares might be tricky on some platforms but should work on Android sequentially
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transactionsListeners.values.forEach { it.cancel() }
    }
}

// Extensions centralisées pour éviter les conflits
fun Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(seconds, nanoseconds)
fun Timestamp.toEpochMilliseconds(): Long = (seconds * 1000) + (nanoseconds / 1000000)
