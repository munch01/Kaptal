package com.Muncho.kaptal.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.Transaction
import com.Muncho.kaptal.utils.*
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import com.Muncho.kaptal.viewmodel.MainViewModel
import com.Muncho.kaptal.viewmodel.toInstant
import kotlinx.datetime.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    initialPage: Int = 120,
    onPageChanged: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel,
    detailViewModel: AccountDetailViewModel
) {
    val transactions by detailViewModel.transactions.collectAsState()
    
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

                    MonthPageContent(
                        account = account,
                        year = year,
                        month = month,
                        transactions = transactions,
                        onCheckedChange = { transactionId, monthKey, isChecked ->
                            detailViewModel.toggleTransactionCheck(account.id, transactionId, monthKey, isChecked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MonthPageContent(
    account: Account,
    year: Int,
    month: Int,
    transactions: List<Transaction>,
    onCheckedChange: (String, String, Boolean) -> Unit
) {
    val monthKey = "${year}-${(month + 1).toString().padStart(2, '0')}"
    
    val monthTransactions = remember(transactions, year, month) {
        transactions.filter { tx ->
            val txInstant = tx.date.toInstant()
            val txYear = DateTimeUtils.getYear(txInstant)
            val txMonth = DateTimeUtils.getMonth(txInstant)
            
            if (tx.isRecurring) {
                val startTotal = txYear * 12 + txMonth
                val currentTotal = year * 12 + month
                val endTotal = tx.endDate?.let {
                    val endI = it.toInstant()
                    DateTimeUtils.getYear(endI) * 12 + DateTimeUtils.getMonth(endI)
                } ?: Int.MAX_VALUE
                currentTotal in startTotal until endTotal
            } else {
                txYear == year && txMonth == month
            }
        }.sortedBy { it.date.toInstant() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = getMonthName(year, month),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(monthTransactions) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    isChecked = transaction.isCheckedForMonth(monthKey),
                    onCheckedChange = { onCheckedChange(transaction.id, monthKey, it) }
                )
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onCheckedChange(!isChecked) },
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.title ?: "", fontWeight = FontWeight.Medium)
                Text(transaction.subCategory ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Text(
                text = formatAmount(transaction.amount),
                fontWeight = FontWeight.Bold,
                color = if (transaction.amount >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}
