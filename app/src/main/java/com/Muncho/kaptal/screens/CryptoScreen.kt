package com.Muncho.kaptal.screens

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.AccountsUiState
import com.Muncho.kaptal.MainViewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(
    account: Account,
    onBackClick: () -> Unit = {},
    mainViewModel: MainViewModel = viewModel(),
    detailViewModel: AccountDetailViewModel = viewModel()
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
    
    val totalInvested = remember(transactions, firstHistoryPrice) { 
        mainViewModel.calculateTotalInvestment(account, transactions, firstHistoryPrice) 
    }
    
    // Performance calculée en direct sur le taux actuel
    val performance = remember(transactions, currentRate, firstHistoryPrice) { 
        mainViewModel.calculatePortfolioPerformance(account, transactions, currentRate, firstHistoryPrice) 
    }
    val (gainLoss, gainLossPercent) = performance

    // Points de la courbe (Gain/Perte historique)
    val gainLossPoints = remember(transactions, cryptoHistory, account.initialBalance, account.initialInvestmentEur) {
        if (cryptoHistory.isEmpty()) return@remember emptyList<Float>()
        
        val firstPrice = cryptoHistory.first().second
        val initialQty = account.initialBalance
        // Si l'utilisateur a saisi un coût d'achat initial, on l'utilise, 
        // sinon on part du principe que la PV est de 0 au début de l'historique (30j)
        val initialCost = account.initialInvestmentEur ?: (initialQty * firstPrice)

        cryptoHistory.map { (timestamp, price) ->
            var qtyAtPoint = initialQty
            var costAtPoint = initialCost
            
            transactions.forEach { tx ->
                val txDate = tx.date.toDate()
                val txTime = txDate.time
                
                // Pour chaque transaction, on détermine son "prix d'achat" pour la courbe
                // On essaie de trouver le prix le plus proche dans l'historique si investmentEur est nul
                val txPurchasePrice = tx.investmentEur?.let { if (tx.amount != 0.0) it / abs(tx.amount) else 0.0 }
                    ?: cryptoHistory.minByOrNull { Math.abs(it.first - txTime) }?.second 
                    ?: price

                if (tx.isRecurring) {
                    val endDate = tx.endDate?.toDate()?.time ?: Long.MAX_VALUE
                    val cal = Calendar.getInstance().apply { time = txDate }
                    while (cal.timeInMillis <= timestamp && cal.timeInMillis < endDate) {
                        qtyAtPoint += tx.amount
                        costAtPoint += tx.amount * txPurchasePrice
                        cal.add(Calendar.MONTH, 1)
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
            painter = painterResource(id = R.drawable.fond_kaptal_propre),
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
                                contentDescription = "Ajouter un achat",
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
                // 1. RÉSUMÉ PERFORMANCE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Plus-value / Moins-value", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            
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
                                    text = "%.2f €".format(gainLoss),
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
                                    text = "%+.2f%%".format(gainLossPercent),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Investi (Prix de revient)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("%.2f €".format(totalInvested), fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Valeur Portefeuille", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("%.2f €".format(totalInvested + gainLoss), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. GRAPHIQUE PERFORMANCE RÉELLE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Évolution Profit/Perte (30j)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxSize()) {
                                RealPerformanceCurve(gainLossPoints)
                            }
                        }
                    }
                }

                // 3. HISTORIQUE DES APPORTS (ACHATS)
                item {
                    Text("Historique des achats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val apports = transactions.filter { it.type == "INCOME" || (it.type == "TRANSFER" && it.amount > 0) }
                    .sortedByDescending { it.date }

                items(apports) { tx ->
                    ApportItem(tx, totalInvested)
                }
            }
        }

        if (showAddSheet) {
            AddTransactionBottomSheet(
                accounts = allAccounts,
                currentAccountId = account.id,
                categories = emptyList(), // Pas besoin de catégories standard pour crypto
                cryptoRates = cryptoRates,
                onDismiss = { showAddSheet = false },
                onSave = { title, amount, familyCategory, subCategory, type, paymentMethod, date, isRecurring, recurrenceInterval, endDate, sourceId, targetId, investmentEur, feesPercent ->
                    if (type == "TRANSFER" && targetId != null) {
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
                            feesPercent = feesPercent
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
    }
}

@Composable
fun RealPerformanceCurve(points: List<Float>) {
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chargement de l'historique...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 0f
        
        // On définit l'échelle pour que 0€ soit bien visible et centré
        val absoluteMax = maxOf(Math.abs(minVal), Math.abs(maxVal), 10f)
        
        fun getY(value: Float): Float {
            // Le centre (height/2) représente 0€ de profit/perte
            return height / 2f - (value / absoluteMax) * (height * 0.4f)
        }

        // Ligne de rentabilité (0€)
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
        
        // Point final
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
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.FRANCE).format(tx.date.toDate())
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(tx.title ?: "Achat Crypto", fontWeight = FontWeight.Bold)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                // On affiche le coût réel de l'achat
                val cost = tx.investmentEur ?: tx.amount
                Text("%.2f €".format(cost), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                
                if (tx.feesPercent != null && tx.feesPercent > 0) {
                    Text("%.1f%% de frais".format(tx.feesPercent), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                val percentage = if (totalInvested > 0) (cost / totalInvested) * 100 else 0.0
                Text("%.1f%% du total investi".format(percentage), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
