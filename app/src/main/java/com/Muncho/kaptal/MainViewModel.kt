package com.Muncho.kaptal

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.google.firebase.Timestamp
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// --- ÉTATS DE L'UI ---
sealed interface AccountsUiState {
    object Loading : AccountsUiState
    data class Success(
        val accounts: List<Account>,
        val accountBalances: Map<String, Double> = emptyMap(), // Map: accountId -> soldeRéelActuel
        val creditRemainingDebts: Map<String, Double> = emptyMap() // Map: accountId -> capitalRestantDû
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
    private var lastAccountsList: List<Account> = emptyList() // Cache pour les calculs réactifs

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

    // --- StateFlow pour le taux du Livret A ---
    private val _livretARate = MutableStateFlow(3.0) // Défaut à 3%
    val livretARate: StateFlow<Double> = _livretARate.asStateFlow()

    // --- LISTE DES CRYPTOS POPULAIRES ---
    val popularCryptos = listOf(
        "BTC" to "Bitcoin",
        "ETH" to "Ethereum",
        "SOL" to "Solana",
        "XRP" to "Ripple",
        "ADA" to "Cardano",
        "DOT" to "Polkadot",
        "DOGE" to "Dogecoin",
        "AVAX" to "Avalanche",
        "MATIC" to "Polygon",
        "LINK" to "Chainlink"
    )

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            loadAccounts()
            loadUserSettings()
        } else {
            accountsListener?.remove()
            _uiState.value = AccountsUiState.Error("Utilisateur non connecté.")
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        fetchCryptoRates()
        fetchLivretARate()
    }

    // --- RÉCUPÉRATION DU TAUX LIVRET A (Open Data) ---
    fun fetchLivretARate() {
        viewModelScope.launch {
            try {
                val rate = withContext(Dispatchers.IO) {
                    val url = URL("https://www.data.gouv.fr/fr/datasets/r/f01b058a-36b1-4b71-b0e6-9b7e7c7e39a3")
                    val csvText = url.readText()
                    val lines = csvText.split("\n")
                    if (lines.size > 1) {
                        val dataLine = lines.filter { it.isNotBlank() }.last()
                        val columns = dataLine.split(";")
                        columns[1].replace(",", ".").toDoubleOrNull() ?: 3.0
                    } else 3.0
                }
                _livretARate.value = rate
            } catch (e: Exception) {
                _livretARate.value = 3.0
            }
        }
    }

    // --- RÉCUPÉRATION DES COURS CRYPTO EN DIRECT (COINGECKO) ---
    fun fetchCryptoRates() {
        viewModelScope.launch {
            try {
                val rates = withContext(Dispatchers.IO) {
                    val ids = coinIdMap.values.joinToString(",")
                    val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=eur")
                    val jsonString = url.readText()
                    val jsonObject = JSONObject(jsonString)

                    val map = mutableMapOf<String, Double>()
                    coinIdMap.forEach { (symbol, id) ->
                        if (jsonObject.has(id)) {
                            map[symbol] = jsonObject.getJSONObject(id).getDouble("eur")
                        }
                    }
                    map
                }
                _cryptoRates.value = rates
            } catch (e: Exception) {
                // En cas d'échec réseau, on garde les anciennes valeurs ou une map vide silencieusement
            }
        }
    }

    // --- StateFlow pour l'historique crypto (Liste de paires : Timestamp -> Prix en €) ---
    private val _cryptoHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val cryptoHistory: StateFlow<List<Pair<Long, Double>>> = _cryptoHistory.asStateFlow()

    private val coinIdMap = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "XRP" to "ripple",
        "ADA" to "cardano",
        "DOT" to "polkadot",
        "DOGE" to "dogecoin",
        "AVAX" to "avalanche",
        "MATIC" to "matic-network",
        "LINK" to "chainlink",
        "USDT" to "tether"
    )

    fun fetchCryptoHistory(symbol: String) {
        val coinId = coinIdMap[symbol] ?: return
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    val url = URL("https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=eur&days=30&interval=daily")
                    val jsonString = url.readText()
                    val prices = JSONObject(jsonString).getJSONArray("prices")
                    val list = mutableListOf<Pair<Long, Double>>()
                    for (i in 0 until prices.length()) {
                        val item = prices.getJSONArray(i)
                        list.add(item.getLong(0) to item.getDouble(1))
                    }
                    list
                }
                _cryptoHistory.value = history
            } catch (e: Exception) {
                Log.e("CRYPTO_HISTORY", "Erreur fetch : ${e.localizedMessage}")
            }
        }
    }

    /**
     * Calcule les intérêts pour un compte à rémunération journalière
     */
    fun calculateDailyInterests(
        account: Account, 
        transactions: List<Transaction>, 
        rate: Double,
        untilYear: Int? = null,
        untilMonth: Int? = null,
        onlyChecked: Boolean = true
    ): Double {
        val now = Calendar.getInstance()
        val currentYear = untilYear ?: now.get(Calendar.YEAR)
        
        var totalInterests = 0.0
        val dailyRate = (rate / 100.0) / 365.0
        
        val cal = Calendar.getInstance().apply {
            set(currentYear, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val endCal = Calendar.getInstance()
        if (untilYear != null && untilMonth != null) {
            endCal.set(untilYear, untilMonth, 1)
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        
        // On ne calcule pas au delà d'aujourd'hui pour le "réel"
        val limitCal = if (onlyChecked) now else endCal
        
        while (cal.before(limitCal)) {
            val balance = calculateBalanceAtDate(account, transactions, cal.time, onlyChecked)
            if (balance > 0) {
                totalInterests += balance * dailyRate
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return totalInterests
    }

    /**
     * Génère des points de projection pour une courbe (sur 12 mois)
     */
    fun calculateProjections(account: Account, transactions: List<Transaction>, rate: Double): List<Pair<Long, Double>> {
        val list = mutableListOf<Pair<Long, Double>>()
        val cal = Calendar.getInstance()
        val startBalance = calculateCurrentRealBalance(account, transactions)
        
        var currentProjectedBalance = startBalance
        val monthlyRate = (rate / 100.0) / 12.0
        
        // Point actuel
        list.add(cal.timeInMillis to currentProjectedBalance)
        
        for (i in 1..12) {
            cal.add(Calendar.MONTH, 1)
            currentProjectedBalance += (currentProjectedBalance * monthlyRate)
            
            // Impact des transactions récurrentes pour la projection
            transactions.filter { it.isRecurring }.forEach { tx ->
                if (isTransactionActiveInMonth(tx, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))) {
                    currentProjectedBalance += tx.amount
                }
            }
            
            list.add(cal.timeInMillis to currentProjectedBalance)
        }
        
        return list
    }

    fun calculateBalanceAtDate(account: Account, transactions: List<Transaction>, date: Date, onlyChecked: Boolean): Double {
        var balance = account.initialBalance
        val targetTime = date.time
        
        val cal = Calendar.getInstance()
        
        for (tx in transactions) {
            val txDate = tx.date.toDate()
            if (tx.isRecurring) {
                val iterCal = Calendar.getInstance().apply { time = txDate }
                val endDate = tx.endDate?.toDate()?.time ?: Long.MAX_VALUE
                while (iterCal.timeInMillis <= targetTime && iterCal.timeInMillis < endDate) {
                    val mKey = String.format(Locale.US, "%d-%02d", iterCal.get(Calendar.YEAR), iterCal.get(Calendar.MONTH) + 1)
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        balance += tx.amount
                    }
                    iterCal.add(Calendar.MONTH, 1)
                }
            } else {
                if (txDate.time <= targetTime) {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    val mKey = String.format(Locale.US, "%d-%02d", txCal.get(Calendar.YEAR), txCal.get(Calendar.MONTH) + 1)
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        balance += tx.amount
                    }
                }
            }
        }
        return balance
    }
    /**
     * Calcule les intérêts du Livret A pour l'année en cours
     * Règle des quinzaines : 24 quinzaines par an.
     */
    fun calculateLivretAInterests(
        account: Account, 
        transactions: List<Transaction>, 
        rate: Double,
        untilYear: Int? = null,
        untilMonth: Int? = null,
        onlyChecked: Boolean = true
    ): Double {
        val now = Calendar.getInstance()
        val currentYear = untilYear ?: now.get(Calendar.YEAR)
        
        var totalInterests = 0.0
        val ratePerFortnight = (rate / 100.0) / 24.0

        val targetQuinzaineLimit = if (untilYear != null && untilMonth != null) {
            (untilMonth + 1) * 2
        } else 24

        // On itère sur les quinzaines
        for (q in 0 until targetQuinzaineLimit) {
            val month = q / 2
            val isSecondHalf = q % 2 == 1
            
            val balance = calculateBalanceAtFortnight(account, transactions, currentYear, month, isSecondHalf, onlyChecked)
            
            if (balance > 0) {
                // Pour le projeté, on compte tout. Pour le réel, on s'arrête à aujourd'hui.
                val fortnightDate = Calendar.getInstance().apply {
                    set(currentYear, month, if (isSecondHalf) 16 else 1)
                }
                if (!onlyChecked || fortnightDate.before(now)) {
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
        
        val pivotCal = Calendar.getInstance().apply {
            set(year, month, if (isSecondHalf) 16 else 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pivotDate = pivotCal.time

        for (tx in transactions) {
            if (tx.isRecurring) {
                val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
                var iterCal = txCal.clone() as Calendar
                while (iterCal.time.before(pivotDate)) {
                    val mKey = String.format(Locale.US, "%d-%02d", iterCal.get(Calendar.YEAR), iterCal.get(Calendar.MONTH) + 1)
                    if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                        val valDate = getValueDate(iterCal, tx.amount)
                        if (!valDate.after(pivotDate)) {
                            balance += tx.amount
                        }
                    }
                    iterCal.add(Calendar.MONTH, 1)
                    if (tx.endDate != null && iterCal.time.after(tx.endDate.toDate())) break
                }
            } else {
                val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
                val mKey = String.format(Locale.US, "%d-%02d", txCal.get(Calendar.YEAR), txCal.get(Calendar.MONTH) + 1)
                if (!onlyChecked || tx.isCheckedForMonth(mKey)) {
                    val valDate = getValueDate(txCal, tx.amount)
                    if (!valDate.after(pivotDate)) {
                        balance += tx.amount
                    }
                }
            }
        }
        return balance
    }

    private fun getValueDate(txCal: Calendar, amount: Double): Date {
        val valCal = txCal.clone() as Calendar
        if (amount > 0) { // Dépôt : Date de valeur = début quinzaine suivante
            if (valCal.get(Calendar.DAY_OF_MONTH) <= 15) {
                valCal.set(Calendar.DAY_OF_MONTH, 16)
            } else {
                valCal.add(Calendar.MONTH, 1)
                valCal.set(Calendar.DAY_OF_MONTH, 1)
            }
        } else { // Retrait : Date de valeur = fin quinzaine précédente
            if (valCal.get(Calendar.DAY_OF_MONTH) <= 15) {
                valCal.set(Calendar.DAY_OF_MONTH, 1)
            } else {
                valCal.set(Calendar.DAY_OF_MONTH, 16)
            }
        }
        valCal.set(Calendar.HOUR_OF_DAY, 0)
        valCal.set(Calendar.MINUTE, 0)
        valCal.set(Calendar.SECOND, 0)
        valCal.set(Calendar.MILLISECOND, 0)
        return valCal.time
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

                    lastAccountsList = accountsList // Mise à jour du cache
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
                            // Correction : On utilise TOUJOURS la dernière liste de comptes connue
                            recalculateBalances(lastAccountsList) 
                        }
                    }
                transactionsListeners[account.id] = listener
            }
        }

        recalculateBalances(accounts)
    }

    private fun recalculateBalances(accounts: List<Account>) {
        viewModelScope.launch(Dispatchers.Default) {
            // Sécurité : On utilise la liste passée, mais si elle est obsolète par rapport au cache, on prend le cache
            val targetAccounts = if (accounts.size >= lastAccountsList.size) accounts else lastAccountsList
            
            val balancesMap = targetAccounts.associate { account ->
                val txs = accountTransactionsMap[account.id] ?: emptyList()
                account.id to calculateCurrentRealBalance(account, txs)
            }
            
            val debtsMap = targetAccounts.filter { it.type == "CREDIT" }.associate { account ->
                val txs = accountTransactionsMap[account.id] ?: emptyList()
                val now = Calendar.getInstance().timeInMillis
                
                // On cherche toutes les transactions passées
                val pastTxs = txs.filter { it.type == "INCOME" && it.date.toDate().time <= now }
                
                // On prend la plus récente pour avoir le capital restant dû exact du PDF
                val lastTx = pastTxs.minByOrNull { now - it.date.toDate().time }
                
                val remainingValue = if (lastTx?.remainingDebt != null) {
                    lastTx.remainingDebt
                } else {
                    val initialCapital = account.totalAmount ?: 0.0
                    val totalPrincipalRepaid = pastTxs.sumOf { it.principalPart ?: 0.0 }
                    initialCapital - totalPrincipalRepaid
                }
                
                account.id to (remainingValue ?: 0.0)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = AccountsUiState.Success(targetAccounts, balancesMap, debtsMap)
            }
        }
    }

    private fun calculateCurrentRealBalance(account: Account, transactions: List<Transaction>): Double {
        var total = account.initialBalance
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)
        val targetIndex = currentYear * 12 + currentMonth
        
        val isCrypto = account.type == "CRYPTO"

        total += transactions.sumOf { tx ->
            val txDate = tx.date.toDate()
            val txCal = Calendar.getInstance().apply { time = txDate }
            val startIndex = txCal.get(Calendar.YEAR) * 12 + txCal.get(Calendar.MONTH)

            if (tx.isRecurring) {
                val endIndex = tx.endDate?.let {
                    val endCal = Calendar.getInstance().apply { time = it.toDate() }
                    endCal.get(Calendar.YEAR) * 12 + endCal.get(Calendar.MONTH)
                } ?: Int.MAX_VALUE

                val effectiveEndIndex = minOf(targetIndex, endIndex - 1)

                if (startIndex <= effectiveEndIndex) {
                    if (isCrypto) {
                        // Pour la crypto, on compte toutes les occurrences passées automatiquement
                        val count = (effectiveEndIndex - startIndex + 1).coerceAtLeast(0)
                        count * tx.amount
                    } else {
                        tx.checkedMonths.count { mKey ->
                            try {
                                val parts = mKey.split("-")
                                val y = parts[0].toInt()
                                val m = parts[1].toInt() - 1
                                val mIndex = y * 12 + m
                                mIndex in startIndex..effectiveEndIndex
                            } catch (e: Exception) {
                                false
                            }
                        } * tx.amount
                    }
                } else {
                    0.0
                }
            } else {
                if (startIndex <= targetIndex) {
                    if (isCrypto) {
                        tx.amount
                    } else {
                        val mKey = String.format(Locale.US, "%d-%02d", txCal.get(Calendar.YEAR), txCal.get(Calendar.MONTH) + 1)
                        if (tx.isCheckedForMonth(mKey)) tx.amount else 0.0
                    }
                } else {
                    0.0
                }
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
        linkedAccountId: String? = null,
        cryptoSymbol: String? = null,
        initialInvestmentEur: Double? = null,
        savingsRate: Double? = null, // Nouveau
        onAccountCreated: (String) -> Unit = {}
    ) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                Log.d("ADD_ACCOUNT", "Tentative d'ajout du compte: $name ($type)")
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
                    ownerId = currentUser.uid,
                    linkedAccountId = linkedAccountId,
                    cryptoSymbol = cryptoSymbol,
                    initialInvestmentEur = initialInvestmentEur,
                    savingsRate = savingsRate
                )

                newAccountRef.set(account).await()
                Log.d("ADD_ACCOUNT", "Compte créé avec succès: ${newAccountRef.id}")
                onAccountCreated(account.id)
            } catch (e: Exception) { 
                Log.e("ADD_ACCOUNT", "Erreur lors de la création du compte", e)
            }
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
        linkedAccountId: String? = null,
        cryptoSymbol: String? = null,
        initialInvestmentEur: Double? = null,
        savingsRate: Double? = null, // Nouveau
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d("UPDATE_ACCOUNT", "Mise à jour du compte: $accountId")
                val updates = mutableMapOf<String, Any?>(
                    "name" to name,
                    "bankName" to bankName,
                    "initialBalance" to initialBalance,
                    "type" to type,
                    "isJoint" to isJoint,
                    "color" to color,
                    "linkedAccountId" to linkedAccountId,
                    "cryptoSymbol" to cryptoSymbol,
                    "initialInvestmentEur" to initialInvestmentEur,
                    "savingsRate" to savingsRate
                )
                firestore.collection("accounts").document(accountId).update(updates).await()
                Log.d("UPDATE_ACCOUNT", "Compte mis à jour avec succès")
                onSuccess()
            } catch (e: Exception) { 
                Log.e("UPDATE_ACCOUNT", "Erreur lors de la mise à jour", e)
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                // 1. Récupérer les infos du compte avant suppression pour la cascade
                val accountDoc = firestore.collection("accounts").document(accountId).get().await()
                val type = accountDoc.getString("type")
                val linkedId = accountDoc.getString("linkedAccountId")

                // 2. Si c'est un crédit avec un compte lié, supprimer les transactions miroirs
                if (type == "CREDIT" && !linkedId.isNullOrBlank()) {
                    val linkedTransactions = firestore.collection("accounts")
                        .document(linkedId)
                        .collection("transactions")
                        .whereEqualTo("targetAccountId", accountId)
                        .get()
                        .await()
                    
                    val batch = firestore.batch()
                    linkedTransactions.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }

                // 3. Supprimer les transactions du compte lui-même (Bonne pratique Firestore)
                val selfTransactions = firestore.collection("accounts")
                    .document(accountId)
                    .collection("transactions")
                    .get()
                    .await()
                
                val selfBatch = firestore.batch()
                selfTransactions.forEach { doc -> selfBatch.delete(doc.reference) }
                selfBatch.commit().await()

                // 4. Supprimer le compte
                firestore.collection("accounts").document(accountId).delete().await()
            } catch (e: Exception) { 
                Log.e("DELETE_ACCOUNT_DEBUG", "Erreur lors de la suppression cascade", e)
            }
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

    fun generateInstallments(
        accountId: String,
        linkedAccountId: String?,
        totalMonthly: Double,
        months: Int,
        startDate: Date,
        title: String
    ) {
        viewModelScope.launch {
            try {
                val cal = Calendar.getInstance().apply { time = startDate }
                val now = Calendar.getInstance()
                val currentMonthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val transactionsRef = firestore.collection("accounts").document(accountId).collection("transactions")
                val linkedRef = linkedAccountId?.let { firestore.collection("accounts").document(it).collection("transactions") }

                val batch = firestore.batch()

                for (i in 0 until months) {
                    val date = Timestamp(cal.time)
                    
                    // 1. Crédit (Réduction de la dette) - TOUJOURS CRÉÉ
                    val creditTx = Transaction(
                        title = title,
                        amount = abs(totalMonthly),
                        type = "INCOME",
                        familyCategory = "Crédit",
                        subCategory = "Mensualité",
                        date = date,
                        checkedMonths = emptyList()
                    )
                    batch.set(transactionsRef.document(), creditTx)
                    
                    // 2. Débit (Sortie d'argent du compte lié) - UNIQUEMENT SI >= MOIS EN COURS
                    if (linkedRef != null && !cal.before(currentMonthStart)) {
                        val debitTx = Transaction(
                            title = title,
                            amount = -abs(totalMonthly),
                            type = "EXPENSE",
                            familyCategory = "Crédit",
                            subCategory = "Mensualité",
                            date = date,
                            checkedMonths = emptyList(),
                            targetAccountId = accountId // Pour lien de suppression
                        )
                        batch.set(linkedRef.document(), debitTx)
                    }
                    
                    cal.add(Calendar.MONTH, 1)
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e("CREDIT_GEN_DEBUG", "Erreur lors de la génération des mensualités", e)
            }
        }
    }

    fun calculateTotalInvestment(account: Account, transactions: List<Transaction>, referencePrice: Double = 0.0): Double {
        var total = account.initialInvestmentEur ?: (account.initialBalance * referencePrice)
        val now = Calendar.getInstance()
        val currentMonthIndex = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)

        transactions.forEach { tx ->
            val fiatFlow = tx.investmentEur ?: 0.0
            if (fiatFlow == 0.0) return@forEach
            
            val isMoneyIn = tx.type == "INCOME" || (tx.type == "TRANSFER" && tx.amount > 0)
            val factor = if (isMoneyIn) 1.0 else -1.0

            val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
            val startIndex = txCal.get(Calendar.YEAR) * 12 + txCal.get(Calendar.MONTH)

            if (tx.isRecurring) {
                val endIndex = tx.endDate?.let {
                    val endCal = Calendar.getInstance().apply { time = it.toDate() }
                    endCal.get(Calendar.YEAR) * 12 + endCal.get(Calendar.MONTH)
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

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        accountsListener?.remove()
        transactionsListeners.values.forEach { it.remove() }
    }
}