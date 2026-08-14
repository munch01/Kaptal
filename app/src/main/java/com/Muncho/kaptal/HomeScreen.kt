package com.Muncho.kaptal

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.model.Account
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAddAccount: (String) -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    onExit: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val uiState by viewModel.uiState.collectAsState()
    val selectedCurrency by viewModel.appCurrency.collectAsState()
    val cryptoRates by viewModel.cryptoRates.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    
    var draggingAccountId by remember { mutableStateOf<String?>(null) }

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
                        val titleRes = when(pagerState.currentPage) {
                            0 -> R.string.home_title
                            1 -> R.string.account_type_savings
                            2 -> R.string.account_type_credit
                            else -> R.string.account_type_crypto
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
                        IconButton(onClick = { activity.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.home_quit))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:3lmunch0@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Support Kaptal")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Aucune application d'e-mail trouvée", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Support Email", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
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
                            Button(onClick = { viewModel.loadAccounts() }) { Text(stringResource(R.string.home_retry)) }
                        }
                    }
                    is AccountsUiState.Success -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val filteredAccounts = remember(state.accounts, pageIndex) {
                                when (pageIndex) {
                                    0 -> state.accounts.filter { it.type == "CHECKING" }
                                    1 -> state.accounts.filter { it.type == "SAVINGS" || it.type == "LIVRET_A" }
                                    2 -> state.accounts.filter { it.type == "CREDIT" }
                                    else -> state.accounts.filter { it.type == "CRYPTO" }
                                }
                            }

                            if (filteredAccounts.isEmpty()) {
                                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.home_no_accounts), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
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
                                    items(items = accountsList, key = { account: Account -> account.id }) { account ->
                                        val realBalance = state.accountBalances[account.id] ?: account.initialBalance
                                        val remainingDebt = state.creditRemainingDebts[account.id] ?: 0.0

                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { dismissValue ->
                                                when (dismissValue) {
                                                    SwipeToDismissBoxValue.StartToEnd -> {
                                                        accountToEdit = account
                                                        false
                                                    }
                                                    SwipeToDismissBoxValue.EndToStart -> {
                                                        accountToDelete = account
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

                // Barre de Navigation Flottante, Transparente et Tout-en-un
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
                        // 1. Onglets de gauche (Comptes, Épargne)
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NavTabItem(0, Icons.Default.AccountBalance, R.string.account_type_checking, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            NavTabItem(1, Icons.Default.Savings, R.string.account_type_savings, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                        }

                        // 2. Bouton CENTRAL D'AJOUT (+)
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

                        // 3. Onglets de droite (Crédit, Crypto)
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NavTabItem(2, Icons.Default.CreditCard, R.string.account_type_credit, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }
                            NavTabItem(3, Icons.Default.CurrencyBitcoin, R.string.account_type_crypto, pagerState.currentPage) { 
                                coroutineScope.launch { pagerState.animateScrollToPage(3) }
                            }
                        }
                    }
                }

                // Bouton Café Flottant discret
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 100.dp, start = 20.dp) 
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/elmuncho")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("☕", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Café", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (accountToEdit != null) {
        val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
        AccountFormDialog(
            title = stringResource(R.string.account_edit_title),
            initialAccount = accountToEdit,
            allAccounts = allAccounts,
            onDismiss = { accountToEdit = null },
            onConfirm = { name, bankName, initialBalance, type, isJoint, color, memberEmail, _, linkedAccountId ->
                viewModel.updateAccount(accountToEdit!!.id, name, bankName, initialBalance, type, isJoint, color, linkedAccountId) {
                    if (isJoint && memberEmail.isNotBlank()) {
                        viewModel.addMemberToAccount(accountToEdit!!.id, memberEmail) { _, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    accountToEdit = null
                }
            }
        )
    }

    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(stringResource(R.string.account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm_text, account.name)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { viewModel.deleteAccount(account.id); accountToDelete = null }
                ) { Text(stringResource(R.string.delete_label)) }
            },
            dismissButton = { TextButton(onClick = { accountToDelete = null }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
}

@Composable
private fun NavTabItem(index: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, currentPage: Int, onClick: () -> Unit) {
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
    val accountColor = try {
        Color(android.graphics.Color.parseColor(account.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
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
                            "SAVINGS" -> stringResource(R.string.account_type_savings)
                            "CREDIT" -> stringResource(R.string.account_type_credit)
                            "CRYPTO" -> stringResource(R.string.account_type_crypto)
                            else -> stringResource(R.string.account_type_checking)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account.isJoint) {
                        Text(text = "• " + stringResource(R.string.account_shared), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            val balanceColor = if (currentBalance >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (account.type == "CRYPTO") {
                    val cryptoSymbol = "BTC" 
                    Text(
                        text = String.format(Locale.US, "%.4f %s", currentBalance, cryptoSymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )

                    val rate = cryptoRates[cryptoSymbol] ?: 0.0
                    val estimatedEuro = currentBalance * rate

                    Text(
                        text = String.format(Locale.US, "≈ %.2f %s", estimatedEuro, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else if (account.type == "CREDIT") {
                    Text(
                        text = "Capital restant dû",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Text(
                        text = String.format(Locale.US, "%.2f %s", if (remainingDebt > 0) remainingDebt else 0.0, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = String.format(Locale.US, "%.2f %s", currentBalance, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormDialog(
    title: String,
    initialAccount: Account?,
    allAccounts: List<Account> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, bankName: String, initialBalance: Double, type: String, isJoint: Boolean, color: String, memberEmail: String, cryptoSymbol: String?, linkedAccountId: String?) -> Unit
) {
    var accountName by remember { mutableStateOf(initialAccount?.name ?: "") }
    var bankName by remember { mutableStateOf(initialAccount?.bankName ?: "") }
    var initialBalanceText by remember { mutableStateOf(initialAccount?.initialBalance?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(initialAccount?.type ?: "CHECKING") }
    var linkedAccountId by remember { mutableStateOf(initialAccount?.linkedAccountId) }
    var expandedLinkedAccount by remember { mutableStateOf(false) }

    var isJoint by remember { mutableStateOf(initialAccount?.let { it.isJoint || it.members.size > 1 } ?: false) }
    var selectedColor by remember { mutableStateOf(if (!initialAccount?.color.isNullOrBlank()) initialAccount!!.color else "#2196F3") }
    var memberEmail by remember { mutableStateOf("") }

    val availableColors = listOf("#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text(stringResource(R.string.account_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(stringResource(R.string.account_bank_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                if (selectedType == "CREDIT") {
                    ExposedDropdownMenuBox(
                        expanded = expandedLinkedAccount,
                        onExpandedChange = { expandedLinkedAccount = !expandedLinkedAccount }
                    ) {
                        val selectedAcc = allAccounts.find { it.id == linkedAccountId }
                        OutlinedTextField(
                            value = selectedAcc?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.tx_source_account_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLinkedAccount) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedLinkedAccount,
                            onDismissRequest = { expandedLinkedAccount = false }
                        ) {
                            allAccounts.filter { it.type != "CREDIT" && it.id != initialAccount?.id }.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        linkedAccountId = acc.id
                                        expandedLinkedAccount = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedType != "CREDIT") {
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = { Text(stringResource(R.string.account_initial_balance)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(stringResource(R.string.account_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.forEach { hexColor ->
                        val colorInt = android.graphics.Color.parseColor(hexColor)
                        FilterChip(selected = selectedColor == hexColor, onClick = { selectedColor = hexColor }, label = { Text("●", color = Color(colorInt), style = MaterialTheme.typography.titleLarge) })
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isJoint, onCheckedChange = { isJoint = it })
                    Text(stringResource(R.string.account_shared_checkbox))
                }

                if (isJoint) {
                    OutlinedTextField(
                        value = memberEmail,
                        onValueChange = { memberEmail = it },
                        label = { Text(stringResource(R.string.account_add_member_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = accountName.isNotBlank() && (selectedType == "CREDIT" || initialBalanceText.toDoubleOrNull() != null),
                onClick = {
                    val initialBalance = initialBalanceText.toDoubleOrNull() ?: 0.0
                    onConfirm(accountName.trim(), bankName.trim(), initialBalance, selectedType, isJoint, selectedColor, memberEmail.trim(), null, linkedAccountId)
                }
            ) { Text(stringResource(R.string.account_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
    )
}
