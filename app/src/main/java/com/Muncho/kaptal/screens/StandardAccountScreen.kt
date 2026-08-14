package com.Muncho.kaptal.screens

import android.content.Intent
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.MainViewModel
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

    var showAddSheet by remember { mutableStateOf(false) }
    var showChartSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Pair<Transaction, Pair<Timestamp?, RecurrenceEditScope>>?>(null) }
    var transactionToDelete by remember { mutableStateOf<Pair<Transaction, Timestamp>?>(null) }
    var showEditChoiceDialog by remember { mutableStateOf<Pair<Transaction, Timestamp>?>(null) }

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
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:3lmunch0@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Support Kaptal - ${account.name}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Aucune application d'e-mail trouvée", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "Support Email",
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
                        if (account.type == "LIVRET_A") {
                            val estimatedInterests = remember(transactions, livretARate) {
                                mainViewModel.calculateLivretAInterests(account, transactions, livretARate)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4).copy(alpha = 0.9f)),
                                border = BorderStroke(1.dp, Color(0xFFFBC02D))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.livret_a_interests_title), fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                        Text(stringResource(R.string.livret_a_rate_label, livretARate.toString()), style = MaterialTheme.typography.bodySmall, color = Color(0xFFF57F17))
                                    }
                                    Text("+ %.2f €".format(estimatedInterests), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                    Text(stringResource(R.string.livret_a_rule_notice), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        MonthPageContent(
                            initialBalance = account.initialBalance,
                            year = year,
                            month = month,
                            transactions = transactions,
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
                                    showEditChoiceDialog = Pair(visualTransaction, pivotTimestamp)
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

    showEditChoiceDialog?.let { (transaction, effectiveDate) ->
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
                            transactionToEdit = Pair(transaction, Pair(effectiveDate, RecurrenceEditScope.THIS_AND_FUTURE))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recurrence_future))
                    }
                    TextButton(
                        onClick = {
                            showEditChoiceDialog = null
                            transactionToEdit = Pair(transaction, Pair(effectiveDate, RecurrenceEditScope.THIS_ONLY))
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
            onDismiss = { showAddSheet = false },
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate, targetAccountId ->
                if (type == "TRANSFER" && targetAccountId != null) {
                    detailViewModel.performTransfer(
                        sourceAccountId = account.id,
                        targetAccountId = targetAccountId,
                        amount = amount,
                        title = title,
                        date = date,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval,
                        endDate = endDate
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
                        endDate = endDate
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
            onDismiss = { transactionToEdit = null },
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate, _ ->
                if (transaction.isRecurring && effectiveDate != null && scope != RecurrenceEditScope.ALL) {
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
                        scope = scope
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
                        endDate = endDate
                    )
                    detailViewModel.updateTransaction(account.id, updatedTransaction)
                }
                transactionToEdit = null
            }
        )
    }
}

@Composable
fun CategoryDistributionDialog(
    transactions: List<Transaction>,
    year: Int,
    month: Int,
    onDismiss: () -> Unit
) {
    val activeTransactions = remember(transactions, year, month) {
        transactions.filter { tx -> isTransactionActiveInMonth(tx, year, month) }
    }

    val categoryHierarchy = remember(activeTransactions, year, month) {
        val expenses = activeTransactions.filter { it.amount < 0 }
        val totalExp = expenses.sumOf { kotlin.math.abs(it.amount) }

        expenses.groupBy { it.familyCategory?.ifEmpty { "Autre" } ?: "Autre" }
            .map { (family, familyTxList) ->
                val familyTotal = familyTxList.sumOf { kotlin.math.abs(it.amount) }
                val familyPercentage = if (totalExp > 0) (familyTotal / totalExp) * 100 else 0.0

                val subCategories = familyTxList.groupBy { it.subCategory?.ifEmpty { "Autre" } ?: "Autre" }
                    .map { (sub, subTxList) ->
                        val subTotal = subTxList.sumOf { kotlin.math.abs(it.amount) }
                        val subPercentageOfFamily = if (familyTotal > 0) (subTotal / familyTotal) * 100 else 0.0
                        Triple(sub, subTotal, subPercentageOfFamily)
                    }
                    .sortedByDescending { it.second }

                Quadruple(family, familyTotal, familyPercentage, subCategories)
            }
            .sortedByDescending { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_distribution), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.category_expenses_for, getMonthName(year, month)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (categoryHierarchy.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.category_no_expenses),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryHierarchy) { (family, familyAmount, familyPercentage, subCategories) ->
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = family,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${String.format(Locale.FRANCE, "%.2f", familyAmount)} € (${String.format(Locale.FRANCE, "%.1f", familyPercentage)}%)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { (familyPercentage / 100).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subCategories.forEach { (subCategory, subAmount, subPercentageOfFamily) ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "• $subCategory",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${String.format(Locale.FRANCE, "%.2f", subAmount)} € (${String.format(Locale.FRANCE, "%.1f", subPercentageOfFamily)}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { (subPercentageOfFamily / 100).toFloat() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(CircleShape),
                                                color = MaterialTheme.colorScheme.secondary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.category_close)) }
        }
    )
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
        computeCumulativeBalance(initialBalance, transactions, year, month, onlyChecked = true)
    }

    val projectedBalance = remember(transactions, year, month, initialBalance) {
        computeCumulativeBalance(initialBalance, transactions, year, month, onlyChecked = false)
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

        if (monthTransactions.isEmpty()) {
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

private fun isTransactionActiveInMonth(tx: Transaction, year: Int, month: Int): Boolean {
    val txCal = Calendar.getInstance().apply { time = tx.date.toDate() }
    val txYear = txCal.get(Calendar.YEAR)
    val txMonth = txCal.get(Calendar.MONTH)

    val targetIndex = year * 12 + month
    val startIndex = txYear * 12 + txMonth

    return if (tx.isRecurring) {
        val startsBeforeOrDuring = startIndex <= targetIndex
        val endsAfterOrDuring = if (tx.endDate != null) {
            val endCal = Calendar.getInstance().apply { time = tx.endDate!!.toDate() }
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
fun getCategoryIndicatorColor(family: String?): Color {
    val cleanFamily = family?.lowercase(Locale.ROOT)?.trim() ?: ""
    return when {
        cleanFamily.contains("vital") || cleanFamily.contains("incompressible") -> Color(0xFF2E7D32)
        cleanFamily.contains("confort") || cleanFamily.contains("vie courante") -> Color(0xFF1976D2)
        cleanFamily.contains("superficiel") || cleanFamily.contains("plaisir") -> Color(0xFFE65100)
        cleanFamily.contains("salaire") || cleanFamily.contains("revenu") -> Color(0xFF388E3C)
        else -> Color(0xFF9E9E9E)
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
                        Text(
                            text = transaction.title ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
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

private fun getMonthName(year: Int, month: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(year, month, 1)
    }
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
    return sdf.format(calendar.time).replaceFirstChar { it.uppercase() }
}