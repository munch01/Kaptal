package com.example.kaptal.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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

    // Cache d'état local indexé par l'ID de la transaction
    val localCheckStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(transactions) {
        transactions.forEach { tx ->
            if (!localCheckStates.containsKey(tx.id)) {
                localCheckStates[tx.id] = tx.isChecked
            }
        }
    }

    LaunchedEffect(account.id) {
        viewModel.loadTransactions(account.id)
    }

    // Initialisation du pager avec la page mémorisée par le MainViewModel
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 240 }
    )

    // Notifie le MainViewModel dès que la page courante change
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    val currentCalendar = remember { Calendar.getInstance() }
    val baseYear = remember { currentCalendar.get(Calendar.YEAR) }
    val baseMonth = remember { currentCalendar.get(Calendar.MONTH) }

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
                val cal = remember(page) {
                    Calendar.getInstance().apply {
                        set(baseYear, baseMonth + monthOffset, 1)
                    }
                }
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)

                MonthPageContent(
                    year = year,
                    month = month,
                    transactions = transactions,
                    localCheckStates = localCheckStates,
                    onCheckedChange = { transactionId, isChecked ->
                        localCheckStates[transactionId] = isChecked
                        viewModel.toggleTransactionCheck(account.id, transactionId, isChecked)
                    },
                    onEditClick = { transaction ->
                        transactionToEdit = transaction
                    }
                )
            }
        }
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
                    isChecked = false,
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
    year: Int,
    month: Int,
    transactions: List<Transaction>,
    localCheckStates: Map<String, Boolean>,
    onCheckedChange: (String, Boolean) -> Unit,
    onEditClick: (Transaction) -> Unit
) {
    val monthTransactions = remember(transactions, year, month) {
        transactions.filter { tx ->
            val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
            txCal.get(Calendar.YEAR) == year && txCal.get(Calendar.MONTH) == month
        }
    }

    val realBalance = remember(monthTransactions, localCheckStates) {
        monthTransactions.filter { tx ->
            localCheckStates[tx.id] ?: tx.isChecked
        }.sumOf { it.amount }
    }

    val projectedBalance = remember(monthTransactions) { monthTransactions.sumOf { it.amount } }

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
                items(monthTransactions, key = { it.id }) { transaction ->
                    val isChecked = localCheckStates[transaction.id] ?: transaction.isChecked
                    TransactionItem(
                        transaction = transaction,
                        isChecked = isChecked,
                        onCheckedChange = { checked ->
                            onCheckedChange(transaction.id, checked)
                        },
                        onEditClick = {
                            onEditClick(transaction)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit
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
                    onCheckedChange = { newChecked ->
                        onCheckedChange(newChecked)
                    }
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
                    text = "${if (transaction.amount > 0) "+" else ""}${String.format("%.2f", transaction.amount)} €",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
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