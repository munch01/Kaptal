package com.example.kaptal.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptal.R
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
    var showChartSheet by remember { mutableStateOf(false) }
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

    // Box racine pour superposer l'image de fond et le contenu de l'application
    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. IMAGE DE FOND (PLAN TECHNIQUE & LOGO KAPTAL) ---
        Image(
            painter = painterResource(id = R.drawable.fond_kaptal),
            contentDescription = "Fond d'écran Kaptal",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // --- 2. CONTENU DE L'ÉCRAN ---
        Scaffold(
            containerColor = Color.Transparent, // Transparent pour laisser voir le fond
            topBar = {
                TopAppBar(
                    title = { Text(account.name, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FloatingActionButton(
                        onClick = { showChartSheet = true },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Répartition par catégorie")
                    }

                    FloatingActionButton(
                        onClick = { showAddSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter une opération")
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
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
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate ->
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
            onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate ->
                val updatedTransaction = transaction.copy(
                    title = title,
                    amount = amount,
                    familyCategory = familyCategory,
                    subCategory = subCategory,
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

        expenses.groupBy { it.familyCategory.ifEmpty { "Autre" } }
            .map { (family, familyTxList) ->
                val familyTotal = familyTxList.sumOf { kotlin.math.abs(it.amount) }
                val familyPercentage = if (totalExp > 0) (familyTotal / totalExp) * 100 else 0.0

                val subCategories = familyTxList.groupBy { it.subCategory.ifEmpty { "Autre" } }
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
        title = {
            Text("Répartition par catégorie", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dépenses pour ${getMonthName(year, month)}",
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
                            text = "Aucune dépense ce mois-ci",
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = family,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                        .height(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    subCategories.forEach { (subCategory, subAmount, subPercentageOfFamily) ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "• $subCategory",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${String.format(Locale.FRANCE, "%.2f", subAmount)} € (${String.format(Locale.FRANCE, "%.1f", subPercentageOfFamily)}% de la famille)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            LinearProgressIndicator(
                                                progress = { (subPercentageOfFamily / 100).toFloat() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp),
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
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

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
                border = BorderStroke(
                    1.dp,
                    if (realBalance >= 0) Color(0xFFD1E7DD) else Color(0xFFF8D7DA)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Solde Réel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                border = BorderStroke(
                    1.dp,
                    if (projectedBalance >= 0) Color(0xFFD1E7DD) else Color(0xFFF8D7DA)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Solde Projeté", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun getCategoryPastelColor(family: String): Color {
    return Color.White.copy(alpha = 0.9f) // Légèrement transparent pour fondre avec l'arrière-plan
}

@Composable
fun getCategoryIndicatorColor(family: String): Color {
    val cleanFamily = family.lowercase(Locale.ROOT).trim()
    return when {
        cleanFamily.contains("vital") || cleanFamily.contains("incompressible") ->
            Color(0xFF2E7D32)
        cleanFamily.contains("confort") || cleanFamily.contains("vie courante") ->
            Color(0xFF1976D2)
        cleanFamily.contains("superficiel") || cleanFamily.contains("plaisir") ->
            Color(0xFFE65100)
        cleanFamily.contains("salaire") || cleanFamily.contains("revenu") ->
            Color(0xFF388E3C)
        else -> Color(0xFF9E9E9E)
    }
}

@Composable
fun getCategoryBorderColor(family: String): Color {
    return Color(0xFFE0E0E0)
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint
                )
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = getCategoryPastelColor(transaction.familyCategory)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, getCategoryBorderColor(transaction.familyCategory))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(getCategoryIndicatorColor(transaction.familyCategory))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = onCheckedChange,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = transaction.title,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${if (transaction.amount > 0) "+" else ""}${String.format(Locale.FRANCE, "%.2f", transaction.amount)} €",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (transaction.amount >= 0) positiveColor else negativeColor,
                        modifier = Modifier.padding(end = 12.dp)
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