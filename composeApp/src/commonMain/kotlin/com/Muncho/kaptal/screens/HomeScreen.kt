package com.muncho.kaptal.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.utils.*
import com.muncho.kaptal.viewmodel.AccountsUiState
import com.muncho.kaptal.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAddAccount: (String) -> Unit = {},
    onNavigateToEditAccount: (String) -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    viewModel: MainViewModel
) {
    val platform = getPlatform()
    val haptic = LocalHapticFeedback.current
    
    val uiState by viewModel.uiState.collectAsState()
    val selectedCurrency by viewModel.appCurrency.collectAsState()
    val cryptoRates by viewModel.cryptoRates.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    var draggingAccountId by remember { mutableStateOf<String?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

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
                        val titleRes = when(pagerState.currentPage) {
                            0 -> Res.string.home_title
                            1 -> Res.string.account_type_savings
                            2 -> Res.string.account_type_credit
                            else -> Res.string.account_type_crypto
                        }
                        Text(
                            stringResource(titleRes),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            try {
                                platform.exit() 
                            } catch (e: Exception) {
                                // Fallback silencieux en cas de problème d'abstraction
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(Res.string.home_quit))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            platform.openEmail("3lmunch0@gmail.com", "Support Kaptal")
                        }) {
                            Icon(Icons.Default.Email, contentDescription = "Support Email")
                        }
                        IconButton(onClick = { onNavigateToSettings() }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (val state = uiState) {
                    is AccountsUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is AccountsUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadAccounts() }) { Text(stringResource(Res.string.home_retry)) }
                        }
                    }
                    is AccountsUiState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize().weight(1f)
                            ) { pageIndex ->
                                val filteredAccounts = remember(state.accounts, pageIndex) {
                                    when (pageIndex) {
                                        0 -> state.accounts.filter { it.type == "CHECKING" }
                                        1 -> state.accounts.filter { 
                                            it.type == "SAVINGS" || it.type == "LIVRET_A" || it.type == "SAVINGS_DAILY" || it.type == "BROKERAGE" 
                                        }
                                        2 -> state.accounts.filter { it.type == "CREDIT" }
                                        else -> state.accounts.filter { it.type == "CRYPTO" }
                                    }.sortedBy { it.order }
                                }

                                if (filteredAccounts.isEmpty()) {
                                    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(stringResource(Res.string.home_no_accounts), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                    }
                                } else {
                                    var accountsList by remember(filteredAccounts) {
                                        mutableStateOf(filteredAccounts.sortedBy { it.order })
                                    }
                                    val listState = rememberLazyListState()

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
                                    ) {
                                        if (pageIndex == 3 && accountsList.isNotEmpty()) {
                                            item {
                                                val totalCryptoEur = accountsList.sumOf { account ->
                                                    val quantity = state.accountBalances[account.id] ?: account.initialBalance
                                                    val rate = cryptoRates[account.cryptoSymbol ?: ""] ?: 0.0
                                                    quantity * rate
                                                }
                                                
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(24.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                ) {
                                                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(stringResource(Res.string.crypto_portfolio_total_value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text("${totalCryptoEur.roundTo(2)} $selectedCurrency", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    }
                                                }
                                            }
                                        }

                                        items(items = accountsList, key = { account -> account.id }) { account ->
                                            val realBalance = state.accountBalances[account.id] ?: account.initialBalance
                                            val remainingDebt = state.creditRemainingDebts[account.id] ?: 0.0

                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { dismissValue ->
                                                    when (dismissValue) {
                                                        SwipeToDismissBoxValue.StartToEnd -> {
                                                            onNavigateToEditAccount(account.id)
                                                            false
                                                        }
                                                        SwipeToDismissBoxValue.EndToStart -> {
                                                            accountToDelete = account
                                                            false
                                                        }
                                                        else -> false
                                                    }
                                                }
                                            )

                                            SwipeToDismissBox(
                                                state = dismissState,
                                                backgroundContent = {
                                                    val direction = dismissState.dismissDirection
                                                    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                                    val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Edit else Icons.Default.Delete
                                                    val color = if (direction == SwipeToDismissBoxValue.StartToEnd) Color(0xFF1976D2) else Color(0xFFC62828)
                                                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = alignment) {
                                                        Icon(icon, contentDescription = null, tint = color)
                                                    }
                                                }
                                            ) {
                                                AccountCard(
                                                    account = account,
                                                    currentBalance = realBalance,
                                                    remainingDebt = remainingDebt,
                                                    currency = selectedCurrency,
                                                    cryptoRates = cryptoRates,
                                                    onClick = { onAccountClick(account) },
                                                    modifier = Modifier
                                                        .animateItem()
                                                        .graphicsLayer {
                                                            if (draggingAccountId == account.id) {
                                                                scaleX = 1.05f
                                                                scaleY = 1.05f
                                                                alpha = 0.9f
                                                            }
                                                        }
                                                        .pointerInput(accountsList) {
                                                            var verticalOffset = 0f
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { 
                                                                    draggingAccountId = account.id
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onDragEnd = { 
                                                                    draggingAccountId = null
                                                                    viewModel.updateAccountsOrder(accountsList) 
                                                                },
                                                                onDragCancel = { 
                                                                    draggingAccountId = null
                                                                    viewModel.updateAccountsOrder(accountsList) 
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    verticalOffset += dragAmount.y
                                                                    val threshold = 180f 
                                                                    if (verticalOffset > threshold) {
                                                                        val currentIndex = accountsList.indexOf(account)
                                                                        if (currentIndex < accountsList.size - 1) {
                                                                            accountsList = accountsList.toMutableList().apply {
                                                                                add(currentIndex + 1, removeAt(currentIndex))
                                                                            }
                                                                            verticalOffset = 0f
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        }
                                                                    } else if (verticalOffset < -threshold) {
                                                                        val currentIndex = accountsList.indexOf(account)
                                                                        if (currentIndex > 0) {
                                                                            accountsList = accountsList.toMutableList().apply {
                                                                                add(currentIndex - 1, removeAt(currentIndex))
                                                                            }
                                                                            verticalOffset = 0f
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.88f),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NavTabItem(0, Icons.Default.AccountBalance, Res.string.account_type_checking, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            NavTabItem(1, Icons.Default.Savings, Res.string.account_type_savings, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                        }

                        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                            FilledIconButton(
                                onClick = {
                                    val typeKey = when(pagerState.currentPage) {
                                        0 -> "CHECKING"
                                        1 -> "SAVINGS"
                                        2 -> "CREDIT"
                                        else -> "CRYPTO"
                                    }
                                    onNavigateToAddAccount(typeKey)
                                },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }

                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NavTabItem(2, Icons.Default.CreditCard, Res.string.account_type_credit, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }
                            NavTabItem(3, Icons.Default.CurrencyBitcoin, Res.string.account_type_crypto, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(3) }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 100.dp, start = 20.dp) 
                        .clickable {
                            platform.openUrl("https://ko-fi.com/elmuncho")
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("☕", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.home_kofi), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                if (accountToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { accountToDelete = null },
                        title = { Text(stringResource(Res.string.account_delete_confirm_title)) },
                        text = { Text(stringResource(Res.string.account_delete_confirm_text, accountToDelete?.name ?: "")) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    accountToDelete?.let { viewModel.deleteAccount(it.id) }
                                    accountToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(Res.string.delete_label))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { accountToDelete = null }) {
                                Text(stringResource(Res.string.cancel_label))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(index: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: org.jetbrains.compose.resources.StringResource, currentPage: Int, onClick: () -> Unit) {
    val isSelected = index == currentPage
    val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
    
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
        Text(stringResource(labelRes), fontSize = 9.sp, color = color, maxLines = 1)
    }
}

@Composable
fun AccountCard(
    account: Account,
    currentBalance: Double,
    remainingDebt: Double = 0.0,
    currency: String,
    cryptoRates: Map<String, Double>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountColor = remember(account.color) {
        try {
            if (account.color.isNotBlank()) parseHexColor(account.color) else Color(0xFF9E9E9E)
        } catch (e: Exception) {
            Color(0xFF9E9E9E) // Fallback gris en cas d'erreur de parsing
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp, 44.dp).background(accountColor, shape = MaterialTheme.shapes.small))
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (account.bankName.isNotBlank()) {
                    Text(text = account.bankName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (account.type) {
                            "SAVINGS" -> stringResource(Res.string.account_type_savings)
                            "LIVRET_A" -> stringResource(Res.string.account_type_livret_a)
                            "SAVINGS_DAILY" -> stringResource(Res.string.account_type_remunerated)
                            "BROKERAGE" -> stringResource(Res.string.account_type_brokerage)
                            "CREDIT" -> stringResource(Res.string.account_type_credit)
                            "CRYPTO" -> stringResource(Res.string.account_type_crypto)
                            else -> stringResource(Res.string.account_type_checking)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account.isJoint) {
                        Text(text = "• " + stringResource(Res.string.account_shared), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            val balanceColor = if (currentBalance >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (account.type == "CRYPTO") {
                    val cryptoSymbol = (account.cryptoSymbol ?: "BTC").uppercase() 
                    Text(
                        text = "${currentBalance.roundTo(4)} $cryptoSymbol",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                    val rate = cryptoRates[cryptoSymbol] ?: 0.0
                    if (rate > 0) {
                        val estimatedEuro = currentBalance * rate
                        Text(
                            text = "≈ ${estimatedEuro.roundTo(2)} $currency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else if (account.type == "CREDIT") {
                    Text(
                        text = stringResource(Res.string.credit_remaining_debt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${(if (remainingDebt > 0) remainingDebt else 0.0).roundTo(2)} ${currency}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "${currentBalance.roundTo(2)} ${currency}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                }
            }
        }
    }
}
