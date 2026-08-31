package com.muncho.kaptal.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.viewmodel.AccountDetailViewModel
import com.muncho.kaptal.viewmodel.MainViewModel
import com.muncho.kaptal.viewmodel.AccountsUiState
import com.muncho.kaptal.utils.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.*
import kotlinx.datetime.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(
    account: Account,
    userCategories: List<com.muncho.kaptal.model.CategoryFamily> = emptyList(),
    onBackClick: () -> Unit = {},
    mainViewModel: MainViewModel,
    detailViewModel: AccountDetailViewModel
) {
    val transactions by detailViewModel.transactions.collectAsState()
    val cryptoRates by mainViewModel.cryptoRates.collectAsState()
    val cryptoHistory by mainViewModel.cryptoHistory.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()
    
    val allAccounts = remember(uiState) {
        if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
    }

    var showAddSheet by remember { mutableStateOf(false) }

    val symbol = account.cryptoSymbol ?: "BTC"

    LaunchedEffect(account.id, symbol) {
        detailViewModel.loadTransactions(account.id)
        mainViewModel.fetchCryptoHistory(symbol)
    }

    val currentRate = cryptoRates[symbol] ?: 0.0
    val firstHistoryPrice = remember(cryptoHistory) { cryptoHistory.firstOrNull()?.second ?: 0.0 }
    
    val totalInvested = remember(transactions, currentRate, firstHistoryPrice) { 
        mainViewModel.calculateTotalInvestment(account, transactions, firstHistoryPrice) 
    }
    
    val currentQuantity = remember(transactions) {
        // Simple recalculation of balance for display in header
        var qty = account.initialBalance
        val now = DateTimeUtils.now()
        transactions.forEach { tx ->
            val txInstant = tx.date.toInstant()
            if (tx.isRecurring) {
                val endI = tx.endDate?.toInstant() ?: Instant.DISTANT_FUTURE
                val stepVal = when {
                    tx.recurrenceInterval == "QUARTERLY" -> 3
                    tx.recurrenceInterval == "ANNUAL" -> 12
                    tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                    else -> 1
                }
                val effectiveStep = if (stepVal < 1) 1 else stepVal
                var count = 0
                var occurrence = txInstant
                while (occurrence <= now && occurrence < endI) {
                    qty += tx.amount
                    count++
                    occurrence = DateTimeUtils.addMonths(txInstant, count * effectiveStep)
                }
            } else {
                if (txInstant <= now) qty += tx.amount
            }
        }
        qty
    }

    val performance = remember(transactions, currentRate, firstHistoryPrice) { 
        mainViewModel.calculatePortfolioPerformance(account, transactions, currentRate, firstHistoryPrice) 
    }
    val (gainLoss, gainLossPercent) = performance

    val gainLossPoints = remember(transactions, cryptoHistory, account.initialBalance, account.initialInvestmentEur) {
        if (cryptoHistory.isEmpty()) return@remember emptyList<Float>()
        
        val firstPrice = cryptoHistory.first().second
        val initialQty = account.initialBalance
        val initialCost = account.initialInvestmentEur ?: (initialQty * firstPrice)

        cryptoHistory.map { (timestamp, price) ->
            var qtyAtPoint = initialQty
            var costAtPoint = initialCost
            
            transactions.forEach { tx ->
                val txInstant = tx.date.toInstant()
                val txTime = txInstant.toEpochMilliseconds()
                
                val txPurchasePrice = tx.investmentEur?.let { if (tx.amount != 0.0) it / abs(tx.amount) else 0.0 }
                    ?: cryptoHistory.minByOrNull { abs(it.first - txTime) }?.second 
                    ?: price

                if (tx.isRecurring) {
                    val endDate = tx.endDate?.toInstant()?.toEpochMilliseconds() ?: Long.MAX_VALUE
                    val step = when {
                        tx.recurrenceInterval == "QUARTERLY" -> 3
                        tx.recurrenceInterval == "ANNUAL" -> 12
                        tx.recurrenceInterval?.startsWith("CUSTOM_") == true -> {
                            tx.recurrenceInterval.substringAfter("CUSTOM_").toIntOrNull() ?: 1
                        }
                        else -> 1
                    }
                    val effectiveStep = if (step < 1) 1 else step
                    var count = 0
                    var currentOccurrence = txInstant
                    while (currentOccurrence.toEpochMilliseconds() <= timestamp && currentOccurrence.toEpochMilliseconds() < endDate) {
                        qtyAtPoint += tx.amount
                        costAtPoint += tx.amount * txPurchasePrice
                        count++
                        currentOccurrence = DateTimeUtils.addMonths(txInstant, count * effectiveStep)
                    }
                } else {
                    if (txTime <= timestamp) {
                        qtyAtPoint += tx.amount
                        costAtPoint += tx.amount * txPurchasePrice
                    }
                }
            }
            
            val valueAtPoint = qtyAtPoint * price
            (valueAtPoint - costAtPoint).toFloat()
        }
    }

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
            alpha = 0.2f
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = account.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.home_add),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${currentQuantity.roundTo(6)} $symbol",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(Res.string.crypto_gain_loss), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            
                            val isPositive = gainLoss >= 0
                            val color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${gainLoss.roundTo(2)} €",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = color
                                )
                            }
                            
                            Surface(
                                color = color.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${if(gainLossPercent>=0) "+" else ""}${gainLossPercent.roundTo(2)}%",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(stringResource(Res.string.crypto_invested), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("${totalInvested.roundTo(2)} €", fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(Res.string.crypto_value), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("${(totalInvested + gainLoss).roundTo(2)} €", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(Res.string.crypto_evolution_30d), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxSize()) {
                                RealPerformanceCurve(gainLossPoints)
                            }
                        }
                    }
                }

                item {
                    Text(stringResource(Res.string.crypto_history_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val apports = transactions.filter { it.type == "INCOME" || (it.type == "TRANSFER" && it.amount > 0) }
                    .sortedBy { it.date.toInstant() }

                items(apports) { tx ->
                    ApportItem(tx, totalInvested)
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
                            cryptoRate = rateValue,
                            familyCategory = familyCategory,
                            subCategory = subCategory
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
                            recurrenceInterval = recurrenceInterval ?: "MONTHLY",
                            endDate = endDate,
                            investmentEur = investmentEur,
                            feesPercent = feesPercent
                        )
                        detailViewModel.addTransaction(account.id, newTransaction)
                    }
                }
            )
        }
    }
}

@Composable
fun RealPerformanceCurve(points: List<Float>) {
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.common_loading), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 0f
        
        val absoluteMax = maxOf(abs(minVal), abs(maxVal), 10f)
        
        fun getY(value: Float): Float {
            return height / 2f - (value / absoluteMax) * (height * 0.4f)
        }

        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = androidx.compose.ui.geometry.Offset(0f, height / 2f),
            end = androidx.compose.ui.geometry.Offset(width, height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        val path = Path()
        val stepX = width / (points.size - 1)
        
        points.forEachIndexed { i, valAtPoint ->
            val x = i * stepX
            val y = getY(valAtPoint)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        val lastVal = points.last()
        val curveColor = if (lastVal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

        drawPath(
            path = path,
            color = curveColor,
            style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        
        drawCircle(
            color = curveColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(width, getY(lastVal))
        )
    }
}

@Composable
fun ApportItem(tx: Transaction, totalInvested: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val dateStr = DateTimeUtils.formatDate(tx.date.toInstant(), "dd/MM/yyyy")
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(tx.title ?: stringResource(Res.string.crypto_purchase_default), fontWeight = FontWeight.Bold)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val cost = tx.investmentEur ?: tx.amount
                val amountColor = if (tx.type == "TRANSFER") Color(0xFFFF9800) else Color(0xFF2E7D32)
                
                Text("${cost.roundTo(2)} €", fontWeight = FontWeight.Bold, color = amountColor)
                
                if (tx.feesPercent != null && tx.feesPercent!! > 0) {
                    Text(stringResource(Res.string.crypto_fees_percent, tx.feesPercent!!), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                val percentage = if (totalInvested > 0) (cost / totalInvested) * 100 else 0.0
                Text(stringResource(Res.string.crypto_total_percentage, percentage), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun dev.gitlive.firebase.firestore.Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(this.seconds, this.nanoseconds)
