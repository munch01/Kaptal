package com.Muncho.kaptal.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.MainViewModel
import com.Muncho.kaptal.SettingsViewModel
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import com.Muncho.kaptal.viewmodel.RecurrenceEditScope
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    initialPage: Int = 120,
    onPageChanged: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
    detailViewModel: AccountDetailViewModel = viewModel()
) {
    val transactions by detailViewModel.transactions.collectAsState()
    val livretARate by mainViewModel.livretARate.collectAsState()
    val cryptoRates by mainViewModel.cryptoRates.collectAsState()
    val categories = viewModel<SettingsViewModel>().userCategories

    var showAddSheet by remember { mutableStateOf(false) }
    var showChartSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Pair<Transaction, Pair<Timestamp?, RecurrenceEditScope>>?>(null) }
    var transactionToDelete by remember { mutableStateOf<Pair<Transaction, Timestamp>?>(null) }
    var showEditChoiceDialog by remember { mutableStateOf<Triple<Transaction, Transaction, Timestamp>?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(account.id) {
        detailViewModel.loadTransactions(account.id)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 240 }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    val baseYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val baseMonth = remember { Calendar.getInstance().get(Calendar.MONTH) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        // 1. Fond général
        Image(
            painter = painterResource(id = R.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        // 2. Logo central en filigrane
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_k_logo),
                contentDescription = "Logo K Kaptal",
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.Fit,
                alpha = 0.15f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showChartSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = stringResource(R.string.account_detail_chart),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.account_detail_add_operation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val monthOffset = page - 120
                    val totalMonths = baseYear * 12 + baseMonth + monthOffset
                    val year = totalMonths / 12
                    val month = totalMonths % 12

                    Column {
                        if (account.type == "LIVRET_A" || account.type == "SAVINGS_DAILY" || account.type == "BROKERAGE") {
                            val rate = if (account.type == "LIVRET_A") livretARate else (account.savingsRate ?: 0.0)
                            
                            val estimatedInterests = remember(transactions, rate, account.type) {
                                when (account.type) {
                                    "LIVRET_A" -> mainViewModel.calculateLivretAInterests(account, transactions, rate)
                                    "SAVINGS_DAILY" -> mainViewModel.calculateDailyInterests(account, transactions, rate)
                                    else -> 0.0 // Courtage : Affiché via la courbe de projection
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4).copy(alpha = 0.9f)),
                                border = BorderStroke(1.dp, Color(0xFFFBC02D))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (account.type == "BROKERAGE") "Projection (12 mois)" else stringResource(R.string.livret_a_interests_title), 
                                            fontWeight = FontWeight.Bold, color = Color(0xFFF57F17)
                                        )
                                        Text(stringResource(R.string.livret_a_rate_label, rate.toString()), style = MaterialTheme.typography.bodySmall, color = Color(0xFFF57F17))
                                    }
                                    
                                    if (account.type == "BROKERAGE") {
                                        val projections = remember(transactions, rate) { 
                                            mainViewModel.calculateProjections(account, transactions, rate) 
                                        }
                                        val points = projections.map { it.second.toFloat() }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                            ProjectionCurve(points)
                                        }
                                        Text(
                                            "Solde estimé dans 1 an : %.2f €".format(projections.last().second),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
                                        Text("+ %.2f €".format(estimatedInterests), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                        Text(
                                            if (account.type == "LIVRET_A") stringResource(R.string.livret_a_rule_notice) else "Calculé au jour le jour",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        MonthPageContent(
                            account = account,
                            initialBalance = account.initialBalance,
                            year = year,
                            month = month,
                            transactions = transactions,
                            mainViewModel = mainViewModel,
                            onCheckedChange = { transactionId, monthKey, isChecked ->
                                detailViewModel.toggleTransactionCheck(account.id, transactionId, monthKey, isChecked)
                            },
                            onEditClick = { transaction ->
                                // On prépare la date "pivot" au 1er du mois pour la logique de série
                                val pivotCal = Calendar.getInstance().apply {
                                    set(year, month, 1, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                val pivotTimestamp = Timestamp(pivotCal.time)

                                // On prépare la date "visuelle" pour le calendrier (on garde le jour d'origine)
                                val visualCal = Calendar.getInstance().apply {
                                    time = transaction.date.toDate()
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                }
                                val visualTransaction = transaction.copy(date = Timestamp(visualCal.time))

                                if (transaction.isRecurring) {
                                    showEditChoiceDialog = Triple(transaction, visualTransaction, pivotTimestamp)
                                } else {
                                    transactionToEdit = Pair(transaction, Pair(null, RecurrenceEditScope.ALL))
                                }
                            },
                            onDeleteClick = { transaction ->
                                val targetCal = Calendar.getInstance().apply {
                                    set(year, month, 1, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                val effectiveTimestamp = Timestamp(targetCal.time)
                                transactionToDelete = Pair(transaction, effectiveTimestamp)
                            }
                        )
                    }
                }
            }
        }

        if (showChartSheet) {
            val currentMonthOffset = pagerState.currentPage - 120
            val totalMonths = baseYear * 12 + baseMonth + currentMonthOffset
            val dialogYear = totalMonths / 12
            val dialogMonth = totalMonths % 12

            CategoryDistributionDialog(
                transactions = transactions,
                year = dialogYear,
                month = dialogMonth,
                onDismiss = { showChartSheet = false }
            )
        }
    }

    showEditChoiceDialog?.let { (originalTx, visualTx, effectiveDate) ->
        AlertDialog(
            onDismissRequest = { showEditChoiceDialog = null },
            title = { Text(stringResource(R.string.recurrence_edit_title)) },
            text = { Text(stringResource(R.string.recurrence_edit_text)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(originalTx, Pair(effectiveDate, RecurrenceEditScope.ALL))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recurrence_all))
                    }
                    TextButton(
                        onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(visualTx, Pair(effectiveDate, RecurrenceEditScope.THIS_AND_FUTURE))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recurrence_future))
                    }
                    TextButton(
                        onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(visualTx, Pair(effectiveDate, RecurrenceEditScope.THIS_ONLY))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recurrence_this_only))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showEditChoiceDialog = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel_label), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        )
    }

    transactionToDelete?.let { (transaction, effectiveDate) ->
        val isRecurringSeries = transaction.isRecurring

        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Text(text = if (isRecurringSeries) stringResource(R.string.recurrence_delete_title) else stringResource(R.string.transaction_delete_title))
            },
            text = {
                Text(
                    text = if (isRecurringSeries) {
                        stringResource(R.string.recurrence_delete_text)
                    } else {
                        stringResource(R.string.transaction_delete_text, transaction.title ?: "")
                    }
                )
            },
            confirmButton = {
                if (isRecurringSeries) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(
                                    accountId = account.id,
                                    transaction = transaction,
                                    effectiveDate = effectiveDate,
                                    scope = RecurrenceEditScope.ALL
                                )
                                transactionToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.recurrence_all), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(
                                    accountId = account.id,
                                    transaction = transaction,
                                    effectiveDate = effectiveDate,
                                    scope = RecurrenceEditScope.THIS_AND_FUTURE
                                )
                                transactionToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.recurrence_future), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(
                                    accountId = account.id,
                                    transaction = transaction,
                                    effectiveDate = effectiveDate,
                                    scope = RecurrenceEditScope.THIS_ONLY
                                )
                                transactionToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.recurrence_this_only), color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { transactionToDelete = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cancel_label), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { transactionToDelete = null }) {
                            Text(stringResource(R.string.cancel_label))
                        }
                        TextButton(
                            onClick = {
                                detailViewModel.deleteTransaction(
                                    accountId = account.id,
                                    transaction = transaction,
                                    effectiveDeleteDate = effectiveDate
                                )
                                transactionToDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.delete_label), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        )
    }

    if (showAddSheet) {
        AddTransactionBottomSheet(
            accounts = allAccounts,
            currentAccountId = account.id,
            categories = categories,
            cryptoRates = cryptoRates,
            onDismiss = { showAddSheet = false },
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate, sourceId, targetId, investmentEur, feesPercent ->
                if (type == "TRANSFER" && targetId != null) {
                    val targetAcc = allAccounts.find { it.id == targetId }
                    val currentAcc = allAccounts.find { it.id == account.id }
                    val cryptoAcc = if (currentAcc?.type == "CRYPTO") currentAcc else if (targetAcc?.type == "CRYPTO") targetAcc else null
                    val rate = cryptoAcc?.cryptoSymbol?.let { cryptoRates[it] }

                    detailViewModel.performTransfer(
                        sourceAccountId = sourceId,
                        targetAccountId = targetId,
                        amount = amount,
                        title = title,
                        date = date,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval,
                        endDate = endDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent,
                        cryptoRate = rate
                    )
                } else {
                    val newTransaction = Transaction(
                        title = title,
                        amount = amount,
                        familyCategory = familyCategory,
                        subCategory = subCategory,
                        type = type,
                        paymentMethod = paymentMethod,
                        date = date,
                        checkedMonths = emptyList(),
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval ?: "Mensuel",
                        endDate = endDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent
                    )
                    detailViewModel.addTransaction(account.id, newTransaction)
                }
            }
        )
    }

    transactionToEdit?.let { (transaction, editInfo) ->
        val (effectiveDate, scope) = editInfo
        AddTransactionBottomSheet(
            initialTransaction = transaction,
            accounts = allAccounts,
            currentAccountId = account.id,
            categories = categories,
            cryptoRates = cryptoRates,
            onDismiss = { transactionToEdit = null },
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate, sourceId, targetId, investmentEur, feesPercent ->
                if (type == "TRANSFER" && targetId != null) {
                    val targetAcc = allAccounts.find { it.id == targetId }
                    val currentAcc = allAccounts.find { it.id == account.id }
                    val cryptoAcc = if (currentAcc?.type == "CRYPTO") currentAcc else if (targetAcc?.type == "CRYPTO") targetAcc else null
                    val rateValue = cryptoAcc?.cryptoSymbol?.let { cryptoRates[it] }

                    detailViewModel.performTransfer(
                        sourceAccountId = sourceId,
                        targetAccountId = targetId,
                        amount = amount,
                        title = title,
                        date = date,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval,
                        endDate = endDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent,
                        cryptoRate = rateValue
                    )
                } else if (transaction.isRecurring && effectiveDate != null && scope != RecurrenceEditScope.ALL) {
                    detailViewModel.updateRecurringTransactionWithScope(
                        accountId = account.id,
                        oldTransaction = transaction,
                        newTitle = title,
                        newAmount = amount,
                        newFamilyCategory = familyCategory,
                        newSubCategory = subCategory,
                        newType = type,
                        newPaymentMethod = paymentMethod,
                        newDate = date,
                        newIsRecurring = isRecurring,
                        newRecurrenceInterval = recurrenceInterval ?: "Mensuel",
                        newEndDate = endDate,
                        effectiveDate = effectiveDate,
                        scope = scope,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent
                    )
                } else {
                    val updatedTransaction = transaction.copy(
                        title = title,
                        amount = amount,
                        familyCategory = familyCategory,
                        subCategory = subCategory,
                        type = type,
                        paymentMethod = paymentMethod,
                        date = date,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval ?: transaction.recurrenceInterval,
                        endDate = endDate,
                        investmentEur = investmentEur,
                        feesPercent = feesPercent
                    )
                    detailViewModel.updateTransaction(account.id, updatedTransaction)
                }
                transactionToEdit = null
            }
        )
    }
}



@Composable
fun MonthPageContent(
    account: Account,
    initialBalance: Double,
    year: Int,
    month: Int,
    transactions: List<Transaction>,
    mainViewModel: MainViewModel,
    onCheckedChange: (String, String, Boolean) -> Unit,
    onEditClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit
) {
    val monthKey = remember(year, month) {
        String.format(Locale.US, "%d-%02d", year, month + 1)
    }

    val monthTransactions = remember(transactions, year, month) {
        transactions
            .filter { tx -> isTransactionActiveInMonth(tx, year, month) }
            .sortedWith(compareBy<Transaction> { tx ->
                // Tri principal : le jour du mois (Ordre chronologique : 1, 2, 3...)
                val cal = Calendar.getInstance().apply { time = tx.date.toDate() }
                if (tx.isRecurring) {
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                }
                cal.timeInMillis
            }.thenBy { it.id }) // Tri secondaire pour la stabilité
            .toList()
    }

    val realBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(account, initialBalance, transactions, year, month, onlyChecked = true, mainViewModel = mainViewModel)
    }

    val projectedBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(account, initialBalance, transactions, year, month, onlyChecked = false, mainViewModel = mainViewModel)
    }

    val dailyYields = remember(account, transactions, year, month) {
        if (account.type == "SAVINGS_DAILY" && (account.savingsRate ?: 0.0) > 0.0) {
            val list = mutableListOf<Pair<Date, Double>>()
            val cal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance()
            
            val dailyRate = (account.savingsRate!! / 100.0) / 365.0
            
            for (day in 1..daysInMonth) {
                cal.set(Calendar.DAY_OF_MONTH, day)
                if (cal.after(today)) break
                
                val balance = mainViewModel.calculateBalanceAtDate(account, transactions, cal.time, onlyChecked = true)
                if (balance > 0) {
                    list.add(cal.time to (balance * dailyRate))
                }
            }
            list.sortedByDescending { it.first }
        } else emptyList()
    }

    val positiveColor = Color(0xFF2E7D32)
    val negativeColor = Color(0xFFC62828)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getMonthName(year, month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (realBalance >= 0) Color(0xFFF1F8F5).copy(alpha = 0.9f) else Color(0xFFFDF2F2).copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, if (realBalance >= 0) Color(0xFFD1E7DD) else Color(0xFFF8D7DA))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = stringResource(R.string.account_detail_real_balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f €".format(realBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (realBalance >= 0) positiveColor else negativeColor
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (projectedBalance >= 0) Color(0xFFF1F8F5).copy(alpha = 0.9f) else Color(0xFFFDF2F2).copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, if (projectedBalance >= 0) Color(0xFFD1E7DD) else Color(0xFFF8D7DA))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = stringResource(R.string.account_detail_projected_balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f €".format(projectedBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (projectedBalance >= 0) positiveColor else negativeColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.account_detail_operations),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (monthTransactions.isEmpty() && dailyYields.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.account_detail_no_operations), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (dailyYields.isNotEmpty()) {
                    items(items = dailyYields, key = { "yield_${it.first.time}" }) { (date, amount) ->
                        YieldItem(date = date, amount = amount)
                    }
                }

                items(
                    items = monthTransactions,
                    key = { tx -> "${tx.id}_${tx.date.seconds}_$monthKey" }
                ) { transaction ->
                    val isCheckedInThisMonth = transaction.isCheckedForMonth(monthKey)

                    TransactionItem(
                        transaction = transaction,
                        isChecked = isCheckedInThisMonth,
                        positiveColor = positiveColor,
                        negativeColor = negativeColor,
                        onCheckedChange = { checked ->
                            onCheckedChange(transaction.id, monthKey, checked)
                        },
                        onEditClick = { onEditClick(transaction) },
                        onDeleteClick = { onDeleteClick(transaction) }
                    )
                }
            }
        }
    }
}



private fun computeCumulativeBalance(
    account: Account,
    initialBalance: Double,
    transactions: List<Transaction>,
    targetYear: Int,
    targetMonth: Int,
    onlyChecked: Boolean,
    mainViewModel: MainViewModel
): Double {
    val targetIndex = targetYear * 12 + targetMonth
    var total = initialBalance
    
    val isCrypto = account.type == "CRYPTO"
    val isSavings = account.type == "LIVRET_A" || account.type == "SAVINGS_DAILY" || account.type == "BROKERAGE"

    for (tx in transactions) {
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
                if (onlyChecked && !isCrypto) {
                    for (mKey in tx.checkedMonths) {
                        try {
                            val parts = mKey.split("-")
                            val y = parts[0].toInt()
                            val m = parts[1].toInt() - 1
                            val mIndex = y * 12 + m
                            if (mIndex in startIndex..effectiveEndIndex) {
                                total += tx.amount
                            }
                        } catch (e: Exception) {}
                    }
                } else {
                    val count = (effectiveEndIndex - startIndex + 1).coerceAtLeast(0)
                    total += tx.amount * count
                }
            }
        } else {
            if (startIndex <= targetIndex) {
                if (onlyChecked && !isCrypto) {
                    val mKey = String.format(Locale.US, "%d-%02d", txCal.get(Calendar.YEAR), txCal.get(Calendar.MONTH) + 1)
                    if (tx.isCheckedForMonth(mKey)) total += tx.amount
                } else {
                    total += tx.amount
                }
            }
        }
    }

    if (isSavings) {
        val rate = if (account.type == "LIVRET_A") 3.0 else (account.savingsRate ?: 0.0)
        if (rate > 0) {
            val interests = if (account.type == "LIVRET_A") {
                mainViewModel.calculateLivretAInterests(account, transactions, rate, targetYear, targetMonth, onlyChecked)
            } else {
                mainViewModel.calculateDailyInterests(account, transactions, rate, targetYear, targetMonth, onlyChecked)
            }
            total += interests
        }
    }

    return total
}

@Composable
fun ProjectionCurve(points: List<Float>) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        
        val path = androidx.compose.ui.graphics.Path()
        val stepX = width / (points.size - 1)
        
        points.forEachIndexed { i, value ->
            val x = i * stepX
            val y = height - ((value - minVal) / range) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = Color(0xFFF57F17),
            style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YieldItem(date: Date, amount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Date
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = SimpleDateFormat("dd", Locale.FRANCE).format(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Icon orange % +
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.9f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp).align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp)
                        )
                    }
                }

                // Titre
                Column {
                    Text("Rendement net", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        SimpleDateFormat("HH:mm", Locale.FRANCE).format(date).let { if (it == "00:00") "03:15" else it }, // Simulation de l'heure comme dans la capture
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Text(
                text = "+ %.2f €".format(amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    isChecked: Boolean,
    positiveColor: Color,
    negativeColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEditClick()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteClick()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                else -> Icons.Default.Delete
            }
            val tint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onErrorContainer
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(48.dp)
                        .background(getCategoryIndicatorColor(transaction.familyCategory))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date de l'opération (Jour uniquement)
                        Box(
                            modifier = Modifier.width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val txDate = transaction.date.toDate()
                            Text(
                                text = SimpleDateFormat("dd", Locale.FRANCE).format(txDate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Case à cocher ronde
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .background(if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onCheckedChange(!isChecked) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Titre
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction.title ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            if (transaction.feesPercent != null && transaction.feesPercent > 0) {
                                Text(
                                    text = "Frais: %.1f%%".format(transaction.feesPercent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    val isCreditDebit = transaction.type == "EXPENSE" && transaction.familyCategory == "Crédit"
                    val amountColor = if (transaction.amount >= 0) positiveColor else if (isCreditDebit) Color(0xFF1976D2) else negativeColor

                    Text(
                        text = "%.2f €".format(transaction.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                }
            }
        }
    }
}
