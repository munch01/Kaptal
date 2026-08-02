package com.example.kaptal.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptal.model.Account
import com.example.kaptal.model.Transaction
import com.example.kaptal.viewmodel.AccountDetailViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAccountScreen(
    account: Account,
    initialPage: Int = 120,
    onPageChanged: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    viewModel: AccountDetailViewModel = viewModel()
) {
    val transactions by viewModel.transactions.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Pair<Transaction, Timestamp>?>(null) }

    LaunchedEffect(account.id) {
        viewModel.loadTransactions(account.id)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter une opération")
            }
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

                MonthPageContent(
                    initialBalance = account.initialBalance,
                    year = year,
                    month = month,
                    transactions = transactions,
                    onCheckedChange = { transactionId, monthKey, isChecked ->
                        viewModel.toggleTransactionCheck(account.id, transactionId, monthKey, isChecked)
                    },
                    onEditClick = { transaction ->
                        transactionToEdit = transaction
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

    // Dialogue de suppression
    transactionToDelete?.let { (transaction, effectiveDate) ->
        val isRecurringSeries = transaction.isRecurring

        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Text(
                    text = if (isRecurringSeries) "Supprimer la récurrence" else "Supprimer l'opération"
                )
            },
            text = {
                Text(
                    text = if (isRecurringSeries) {
                        "Cette opération est récurrente. Voulez-vous arrêter la récurrence à partir de ce mois ?"
                    } else {
                        "Voulez-vous vraiment supprimer l'opération \"${transaction.title}\" ?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(
                            accountId = account.id,
                            transaction = transaction,
                            effectiveDeleteDate = effectiveDate
                        )
                        transactionToDelete = null
                    }
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showAddSheet) {
        AddTransactionBottomSheet(
            onDismiss = { showAddSheet = false },
            onSave = { title, amount, category, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate ->
                val newTransaction = Transaction(
                    title = title,
                    amount = amount,
                    category = category,
                    type = type,
                    paymentMethod = paymentMethod,
                    date = date,
                    checkedMonths = emptyList(),
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate
                )
                viewModel.addTransaction(account.id, newTransaction)
            }
        )
    }

    transactionToEdit?.let { transaction ->
        AddTransactionBottomSheet(
            initialTransaction = transaction,
            onDismiss = { transactionToEdit = null },
            onSave = { title, amount, category, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate ->
                val updatedTransaction = transaction.copy(
                    title = title,
                    amount = amount,
                    category = category,
                    type = type,
                    paymentMethod = paymentMethod,
                    date = date,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval,
                    endDate = endDate
                )
                viewModel.updateTransaction(account.id, updatedTransaction)
                transactionToEdit = null
            }
        )
    }
}

@Composable
fun MonthPageContent(
    initialBalance: Double,
    year: Int,
    month: Int,
    transactions: List<Transaction>,
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
            .sortedBy { tx -> tx.date.seconds }
            .toList()
    }

    val realBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(initialBalance, transactions, year, month, onlyChecked = true)
    }

    val projectedBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(initialBalance, transactions, year, month, onlyChecked = false)
    }

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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Solde Réel", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f €".format(realBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Solde Projeté", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f €".format(projectedBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Opérations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (monthTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune opération pour ce mois",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = monthTransactions,
                    key = { tx -> "${tx.id}_${tx.date.seconds}_$monthKey" }
                ) { transaction ->
                    val isCheckedInThisMonth = transaction.isCheckedForMonth(monthKey)

                    TransactionItem(
                        transaction = transaction,
                        isChecked = isCheckedInThisMonth,
                        onCheckedChange = { checked ->
                            onCheckedChange(transaction.id, monthKey, checked)
                        },
                        onEditClick = {
                            onEditClick(transaction)
                        },
                        onDeleteClick = {
                            onDeleteClick(transaction)
                        }
                    )
                }
            }
        }
    }
}

private fun isTransactionActiveInMonth(tx: Transaction, year: Int, month: Int): Boolean {
    val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
    val txYear = txCal.get(Calendar.YEAR)
    val txMonth = txCal.get(Calendar.MONTH)

    val targetIndex = year * 12 + month
    val startIndex = txYear * 12 + txMonth

    return if (tx.isRecurring) {
        val startsBeforeOrDuring = startIndex <= targetIndex
        val endsAfterOrDuring = if (tx.endDate != null) {
            val endCal = Calendar.getInstance().apply { time = tx.endDate.toDate() }
            val endIndex = endCal.get(Calendar.YEAR) * 12 + endCal.get(Calendar.MONTH)
            targetIndex < endIndex
        } else {
            true
        }
        startsBeforeOrDuring && endsAfterOrDuring
    } else {
        startIndex == targetIndex
    }
}

private fun computeCumulativeBalance(
    initialBalance: Double,
    transactions: List<Transaction>,
    targetYear: Int,
    targetMonth: Int,
    onlyChecked: Boolean
): Double {
    if (transactions.isEmpty()) return initialBalance

    val targetIndex = targetYear * 12 + targetMonth
    var total = initialBalance

    for (tx in transactions) {
        val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
        val startIndex = txCal.get(Calendar.YEAR) * 12 + txCal.get(Calendar.MONTH)

        if (tx.isRecurring) {
            val endIndex = tx.endDate?.let {
                val endCal = Calendar.getInstance().apply { time = it.toDate() }
                endCal.get(Calendar.YEAR) * 12 + endCal.get(Calendar.MONTH)
            } ?: Int.MAX_VALUE

            val effectiveEndIndex = minOf(targetIndex, endIndex - 1)

            if (startIndex <= effectiveEndIndex) {
                for (mIndex in startIndex..effectiveEndIndex) {
                    val y = mIndex / 12
                    val m = mIndex % 12
                    if (onlyChecked) {
                        val mKey = String.format(Locale.US, "%d-%02d", y, m + 1)
                        if (tx.isCheckedForMonth(mKey)) total += tx.amount
                    } else {
                        total += tx.amount
                    }
                }
            }
        } else {
            if (startIndex <= targetIndex) {
                if (onlyChecked) {
                    val mKey = String.format(Locale.US, "%d-%02d", txCal.get(Calendar.YEAR), txCal.get(Calendar.MONTH) + 1)
                    if (tx.isCheckedForMonth(mKey)) total += tx.amount
                } else {
                    total += tx.amount
                }
            }
        }
    }

    return total
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = transaction.title, fontWeight = FontWeight.Medium)
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (transaction.amount > 0) "+" else ""}${String.format(Locale.FRANCE, "%.2f", transaction.amount)} €",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Éditer l'opération",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer l'opération",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

fun getMonthName(year: Int, month: Int): String {
    val cal = Calendar.getInstance().apply {
        set(year, month, 1)
    }
    val formatter = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
    return formatter.format(cal.time).replaceFirstChar { it.uppercase() }
}