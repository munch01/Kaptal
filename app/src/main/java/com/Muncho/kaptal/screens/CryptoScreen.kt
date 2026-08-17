package com.Muncho.kaptal.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.MainViewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    val currency by mainViewModel.appCurrency.collectAsState()

    LaunchedEffect(account.id) {
        detailViewModel.loadTransactions(account.id)
    }

    val currentRate = cryptoRates["BTC"] ?: 0.0 // À adapter selon le symbole réel du compte
    val totalInvested = remember(transactions) { mainViewModel.calculateTotalInvestment(transactions) }
    val performance = remember(transactions, currentRate) { 
        mainViewModel.calculatePortfolioPerformance(account, transactions, currentRate) 
    }
    
    val (gainLoss, gainLossPercent) = performance

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
                            Text("Gain / Perte Total", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            
                            val isPositive = gainLoss >= 0
                            val color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
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
                                    Text("Total Investi", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("%.2f €".format(totalInvested), fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Valeur Actuelle", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("%.2f €".format(totalInvested + gainLoss), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. GRAPHIQUE PERFORMANCE (COURBE)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            PerformanceCurve(transactions, currentRate)
                        }
                    }
                }

                // 3. LISTE DES APPORTS
                item {
                    Text("Historique des apports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val apports = transactions.filter { it.type == "INCOME" || (it.type == "TRANSFER" && it.amount > 0) }
                    .sortedByDescending { it.date }

                items(apports) { tx ->
                    ApportItem(tx, totalInvested, currentRate)
                }
            }
        }
    }
}

@Composable
fun PerformanceCurve(transactions: List<Transaction>, currentRate: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (transactions.isEmpty()) return@Canvas
        
        // Logique simplifiée de courbe d'évolution
        // On dessine une courbe qui monte ou descend vers le point final actuel
        val path = Path()
        val width = size.width
        val height = size.height
        
        path.moveTo(0f, height * 0.8f)
        path.quadraticTo(width * 0.5f, height * 0.5f, width, height * 0.2f)
        
        drawPath(
            path = path,
            color = Color(0xFF2196F3),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun ApportItem(tx: Transaction, totalInvested: Double, currentRate: Double) {
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
                Text(tx.title ?: "Apport", fontWeight = FontWeight.Bold)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("%.2f €".format(tx.amount), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                val percentage = if (totalInvested > 0) (tx.amount / totalInvested) * 100 else 0.0
                Text("%.1f%% du total".format(percentage), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
