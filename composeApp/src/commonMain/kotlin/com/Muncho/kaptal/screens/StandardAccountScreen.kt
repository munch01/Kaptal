package com.muncho.kaptal.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.utils.*
import com.muncho.kaptal.viewmodel.AccountDetailViewModel
import com.muncho.kaptal.viewmodel.MainViewModel
import com.muncho.kaptal.viewmodel.RecurrenceEditScope
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    userCategories: List<com.muncho.kaptal.model.CategoryFamily> = emptyList(),
    initialPage: Int = 120,
    onPageChanged: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel,
    detailViewModel: AccountDetailViewModel
) {
    val transactions by detailViewModel.transactions.collectAsState()
    val cryptoRates by mainViewModel.cryptoRates.collectAsState()
    
    var showAddSheet by remember { mutableStateOf(false) }
    var showChartDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Pair<Transaction, Pair<Timestamp?, RecurrenceEditScope>>?>(null) }
    var transactionToDelete by remember { mutableStateOf<Pair<Transaction, Timestamp>?>(null) }
    var showEditChoiceDialog by remember { mutableStateOf<Triple<Transaction, Transaction, Timestamp>?>(null) }

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

    val now = DateTimeUtils.now()
    val baseYear = DateTimeUtils.getYear(now)
    val baseMonth = DateTimeUtils.getMonth(now)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        Image(
            painter = painterResource(Res.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_k_logo),
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
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showChartDialog = true }) {
                            Icon(imageVector = Icons.Default.PieChart, contentDescription = null)
                        }
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val monthOffset = page - 120
                    val totalMonths = baseYear * 12 + baseMonth + monthOffset
                    val year = totalMonths / 12
                    val month = totalMonths % 12

                    Column {
                        MonthPageContent(
                            account = account,
                            year = year,
                            month = month,
                            transactions = transactions,
                            mainViewModel = mainViewModel,
                            onCheckedChange = { transactionId, monthKey, isChecked ->
                                detailViewModel.toggleTransactionCheck(account.id, transactionId, monthKey, isChecked)
                            },
                            onEditClick = { transaction ->
                                val pivotInstant = LocalDateTime(year, month + 1, 1, 12, 0, 0).toInstant(TimeZone.UTC)
                                val pivotTimestamp = Timestamp(pivotInstant.toEpochSeconds(), (pivotInstant.nanosecondsOfSecond))

                                val visualInstant = transaction.date.toInstant().toLocalDateTime(TimeZone.UTC).let {
                                    LocalDateTime(year, month + 1, it.dayOfMonth, 12, 0, 0).toInstant(TimeZone.UTC)
                                }
                                val visualTransaction = transaction.copy(date = Timestamp(visualInstant.toEpochSeconds(), visualInstant.nanosecondsOfSecond))

                                if (transaction.isRecurring) {
                                    showEditChoiceDialog = Triple(transaction, visualTransaction, pivotTimestamp)
                                } else {
                                    transactionToEdit = Pair(transaction, Pair(null, RecurrenceEditScope.ALL))
                                }
                            },
                            onDeleteClick = { transaction ->
                                val targetInstant = LocalDateTime(year, month + 1, 1, 12, 0, 0).toInstant(TimeZone.UTC)
                                val effectiveTimestamp = Timestamp(targetInstant.toEpochSeconds(), (targetInstant.nanosecondsOfSecond))
                                transactionToDelete = Pair(transaction, effectiveTimestamp)
                            }
                        )
                    }
                }
            }
        }

        if (showAddSheet) {
            AddTransactionBottomSheet(
                accounts = allAccounts,
                currentAccountId = account.id,
                categories = userCategories,
                cryptoRates = cryptoRates,
                onDismiss = { showAddSheet = false },
                onSave = { title, amount, family, sub, type, method, date, isRec, recInt, end, srcId, targetId, inv, fees ->
                    if (type == "TRANSFER" && targetId != null) {
                        detailViewModel.performTransfer(srcId, targetId, amount, title, date, isRec, recInt, end, inv, fees, cryptoRates[account.cryptoSymbol ?: ""], family, sub)
                    } else {
                        detailViewModel.addTransaction(account.id, Transaction(
                            title = title, amount = amount, familyCategory = family, subCategory = sub,
                            type = type, paymentMethod = method, date = date, checkedMonths = emptyList(),
                            isRecurring = isRec, recurrenceInterval = recInt, endDate = end, investmentEur = inv, feesPercent = fees
                        ))
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
                categories = userCategories,
                cryptoRates = cryptoRates,
                onDismiss = { transactionToEdit = null },
                onSave = { title, amount, family, sub, type, method, date, isRec, recInt, end, srcId, targetId, inv, fees ->
                    if (transaction.isRecurring && effectiveDate != null && scope != RecurrenceEditScope.ALL) {
                        detailViewModel.updateRecurringTransactionWithScope(
                            accountId = account.id, oldTransaction = transaction, newTitle = title, newAmount = amount,
                            newFamilyCategory = family, newSubCategory = sub, newType = type, newPaymentMethod = method,
                            newDate = date, newIsRecurring = isRec, newRecurrenceInterval = recInt ?: "MONTHLY",
                            newEndDate = end, effectiveDate = effectiveDate, scope = scope, investmentEur = inv, feesPercent = fees
                        )
                    } else {
                        detailViewModel.updateTransaction(account.id, transaction.copy(
                            title = title, amount = amount, familyCategory = family, subCategory = sub,
                            type = type, paymentMethod = method, date = date, isRecurring = isRec,
                            recurrenceInterval = recInt, endDate = end, investmentEur = inv, feesPercent = fees
                        ))
                    }
                    transactionToEdit = null
                }
            )
        }

        showEditChoiceDialog?.let { (originalTx, visualTx, effectiveDate) ->
            AlertDialog(
                onDismissRequest = { showEditChoiceDialog = null },
                title = { Text(stringResource(Res.string.recurrence_edit_title)) },
                text = { Text(stringResource(Res.string.recurrence_edit_text)) },
                confirmButton = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(originalTx, Pair(effectiveDate, RecurrenceEditScope.ALL))
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_all)) }
                        TextButton(onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(visualTx, Pair(effectiveDate, RecurrenceEditScope.THIS_AND_FUTURE))
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_future)) }
                        TextButton(onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(visualTx, Pair(effectiveDate, RecurrenceEditScope.THIS_ONLY))
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_this_only)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showEditChoiceDialog = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.cancel_label), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            )
        }

        transactionToDelete?.let { (transaction, effectiveDate) ->
            AlertDialog(
                onDismissRequest = { transactionToDelete = null },
                title = { Text(text = if (transaction.isRecurring) stringResource(Res.string.recurrence_delete_title) else stringResource(Res.string.transaction_delete_title)) },
                text = { Text(text = if (transaction.isRecurring) stringResource(Res.string.recurrence_delete_text) else stringResource(Res.string.transaction_delete_text, transaction.title ?: "")) },
                confirmButton = {
                    if (transaction.isRecurring) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(account.id, transaction, effectiveDate, RecurrenceEditScope.ALL)
                                transactionToDelete = null
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_all), color = Color.Red) }
                            TextButton(onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(account.id, transaction, effectiveDate, RecurrenceEditScope.THIS_AND_FUTURE)
                                transactionToDelete = null
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_future), color = Color.Red) }
                            TextButton(onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(account.id, transaction, effectiveDate, RecurrenceEditScope.THIS_ONLY)
                                transactionToDelete = null
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.recurrence_this_only), color = Color.Red) }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { transactionToDelete = null }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.cancel_label), color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { transactionToDelete = null }) { Text(stringResource(Res.string.cancel_label)) }
                            TextButton(onClick = {
                                detailViewModel.deleteRecurringTransactionWithScope(account.id, transaction, effectiveDate, RecurrenceEditScope.ALL)
                                transactionToDelete = null
                            }) { Text(stringResource(Res.string.delete_label), color = Color.Red) }
                        }
                    }
                }
            )
        }

        if (showChartDialog) {
            val monthOffset = pagerState.currentPage - 120
            val totalMonths = baseYear * 12 + baseMonth + monthOffset
            CategoryDistributionDialog(
                transactions = transactions,
                year = totalMonths / 12,
                month = totalMonths % 12,
                onDismiss = { showChartDialog = false }
            )
        }
    }
}

@Composable
fun MonthPageContent(
    account: Account,
    year: Int,
    month: Int,
    transactions: List<Transaction>,
    mainViewModel: MainViewModel,
    onCheckedChange: (String, String, Boolean) -> Unit,
    onEditClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit
) {
    val monthKey = "${year}-${(month + 1).toString().padStart(2, '0')}"
    
    val monthTransactions = remember(transactions, year, month) {
        val result = mutableListOf<Transaction>()
        transactions.forEach { tx ->
            // Normalize start date to Noon UTC immediately to handle timezone shifts
            val txInstant = DateTimeUtils.toSafeInstant(tx.date.toInstant())
            val txYear = DateTimeUtils.getYear(txInstant)
            val txMonth = DateTimeUtils.getMonth(txInstant)
            
            if (tx.isRecurring) {
                val startTotal = txYear * 12 + txMonth
                val currentTotal = year * 12 + month
                val endI = tx.endDate?.toInstant()?.let { DateTimeUtils.toSafeInstant(it) } ?: Instant.DISTANT_FUTURE
                val endTotal = DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)

                if (currentTotal >= startTotal && currentTotal <= endTotal) {
                    val step = when {
                        tx.recurrenceInterval == "QUARTERLY" -> 3
                        tx.recurrenceInterval == "ANNUAL" -> 12
                        tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                            tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                        }
                        else -> 1
                    }
                    val effectiveStep = if (step < 1) 1 else step
                    
                    if ((currentTotal - startTotal) % effectiveStep == 0) {
                        val diffMonths = currentTotal - startTotal
                        val visualInstant = DateTimeUtils.addMonths(txInstant, diffMonths)
                        result.add(tx.copy(date = Timestamp(visualInstant.epochSeconds, visualInstant.nanosecondsOfSecond)))
                    }
                }
            } else {
                if (txYear == year && txMonth == month) {
                    result.add(tx.copy(date = Timestamp(txInstant.epochSeconds, txInstant.nanosecondsOfSecond)))
                }
            }
        }
        
        // Deduplicate by ID first, then by logical content (Title + Amount + Day)
        // to handle both Firestore artifacts and accidental user double-entries.
        result.distinctBy { it.id }
            .distinctBy { tx ->
                val day = DateTimeUtils.getDayOfMonth(tx.date.toInstant())
                "${tx.title}_${tx.amount}_$day"
            }
            .sortedBy { it.date.toEpochMilliseconds() }
    }

    val initialBalance = account.initialBalance
    val realBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(account, initialBalance, transactions, year, month, onlyChecked = true, mainViewModel = mainViewModel)
    }
    val projectedBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(account, initialBalance, transactions, year, month, onlyChecked = false, mainViewModel = mainViewModel)
    }

    val estimatedInterests = remember(account, transactions, year, month) {
        if (account.type == "LIVRET_A") {
            mainViewModel.calculateLivretAInterests(account, transactions, 3.0, year, 11, onlyChecked = true)
        } else 0.0
    }

    val dailyYields = remember(account, transactions, year, month) {
        if (account.type == "SAVINGS_DAILY" && (account.savingsRate ?: 0.0) > 0.0) {
            val list = mutableListOf<Pair<Instant, Double>>()
            val daysInMonth = DateTimeUtils.getDaysInMonth(year, month)
            val today = DateTimeUtils.now()
            val dailyRate = (account.savingsRate!! / 100.0) / 365.0
            
            for (day in 1..daysInMonth) {
                val iterInstant = LocalDateTime(year, month + 1, day, 12, 0, 0).toInstant(TimeZone.UTC)
                if (iterInstant > today) break
                val balance = mainViewModel.calculateBalanceAtDate(account, transactions, iterInstant, onlyChecked = true)
                if (balance > 0) list.add(iterInstant to (balance * dailyRate))
            }
            list.sortedByDescending { it.first }
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = DateTimeUtils.formatDate(LocalDateTime(year, month + 1, 1, 12, 0, 0).toInstant(TimeZone.UTC), "MMMM yyyy"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (account.type == "LIVRET_A") {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Intérêts cumulés estimés au 31/12", style = MaterialTheme.typography.labelSmall)
                        Text("${estimatedInterests.roundTo(2)} €", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceCard(stringResource(Res.string.account_detail_real_balance), realBalance, Modifier.weight(1f))
            BalanceCard(stringResource(Res.string.account_detail_projected_balance), projectedBalance, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(dailyYields) { (instant, amount) ->
                YieldItem(instant, amount)
            }
            items(monthTransactions) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    isChecked = transaction.isCheckedForMonth(monthKey),
                    onCheckedChange = { onCheckedChange(transaction.id, monthKey, it) },
                    onEditClick = { onEditClick(transaction) },
                    onDeleteClick = { onDeleteClick(transaction) }
                )
            }
        }
    }
}

@Composable
fun BalanceCard(title: String, balance: Double, modifier: Modifier) {
    val color = if (balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = if (balance >= 0) Color(0xFFF1F8F5) else Color(0xFFFDF2F2)),
        border = BorderStroke(1.dp, if (balance >= 0) Color(0xFFD1E7DD) else Color(0xFFF8D7DA))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("${balance.roundTo(2)} €", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun YieldItem(instant: Instant, amount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(DateTimeUtils.formatDate(instant, "dd"), fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFFF9800), shape = CircleShape, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("%", color = Color.White, fontWeight = FontWeight.Black) }
                }
                Column {
                    Text("Rendement net", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("03:15", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Text("+ %.2f €".format(amount), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onEditClick(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDeleteClick(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Edit else Icons.Default.Delete
                val color = if (direction == SwipeToDismissBoxValue.StartToEnd) Color(0xFF1976D2) else Color(0xFFC62828)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 16.dp),
                    contentAlignment = alignment
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(6.dp).height(48.dp).background(getCategoryIndicatorColor(transaction.familyCategory)))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(DateTimeUtils.formatDate(transaction.date.toInstant(), "dd"), fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .background(if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onCheckedChange(!isChecked) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(transaction.title ?: "", fontWeight = FontWeight.Medium, maxLines = 1)
                            if (transaction.feesPercent != null && transaction.feesPercent!! > 0) {
                                Text("Frais: ${transaction.feesPercent!!}%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                    val isCreditDebit = transaction.type == "EXPENSE" && transaction.familyCategory == "Crédit"
                    val amountColor = if (transaction.type == "TRANSFER") {
                        Color(0xFFFF9800) // Orange pour les virements
                    } else if (transaction.amount >= 0) {
                        Color(0xFF2E7D32) // Vert pour revenus
                    } else if (isCreditDebit) {
                        Color(0xFF1976D2) // Bleu pour crédit
                    } else {
                        Color(0xFFC62828) // Rouge pour dépenses
                    }

                    Text("${transaction.amount.roundTo(2)} €", fontWeight = FontWeight.Bold, color = amountColor)
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
        val txInstant = tx.date.toInstant()
        val txYear = DateTimeUtils.getYear(txInstant)
        val txMonth = DateTimeUtils.getMonth(txInstant)
        val startIndex = txYear * 12 + txMonth

        if (tx.isRecurring) {
            val endTotal = tx.endDate?.let {
                val endI = it.toInstant()
                DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)
            } ?: Int.MAX_VALUE
            
            val step = when {
                tx.recurrenceInterval == "QUARTERLY" -> 3
                tx.recurrenceInterval == "ANNUAL" -> 12
                tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                    tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                }
                else -> 1
            }

            var iterTotal = startIndex
            while (iterTotal <= targetIndex && iterTotal <= endTotal) {
                if (onlyChecked && !isCrypto) {
                    val iterYear = iterTotal / 12
                    val iterMonth = iterTotal % 12
                    val mKey = "${iterYear}-${(iterMonth + 1).toString().padStart(2, '0')}"
                    if (tx.isCheckedForMonth(mKey)) total += tx.amount
                } else {
                    total += tx.amount
                }
                iterTotal += step
            }
        } else {
            if (startIndex <= targetIndex) {
                if (onlyChecked && !isCrypto) {
                    val mKey = "${txYear}-${(txMonth + 1).toString().padStart(2, '0')}"
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

private fun Instant.toEpochSeconds(): Long = this.epochSeconds
