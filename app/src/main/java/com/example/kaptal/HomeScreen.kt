package com.example.kaptal

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptal.model.Account
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedCurrency by viewModel.appCurrency.collectAsState()
    val cryptoRates by viewModel.cryptoRates.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.home_quit))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/munch01/Kaptal/issues"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Feedback, contentDescription = stringResource(R.string.home_bug_report))
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
            },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add))
            }
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
                    if (state.accounts.isEmpty()) {
                        Column(modifier = Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.home_no_accounts), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        var accountsList by remember(state.accounts) {
                            mutableStateOf(state.accounts.sortedBy { it.order })
                        }
                        val listState = rememberLazyListState()

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                        ) {
                            items(items = accountsList, key = { account: Account -> account.id }) { account ->
                                val realBalance = state.accountBalances[account.id] ?: account.initialBalance

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
                                        currency = selectedCurrency,
                                        cryptoRates = cryptoRates,
                                        onClick = { onAccountClick(account) },
                                        modifier = Modifier
                                            .animateItem()
                                            .pointerInput(accountsList) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { },
                                                    onDragEnd = { viewModel.updateAccountsOrder(accountsList) },
                                                    onDragCancel = { viewModel.updateAccountsOrder(accountsList) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val currentIndex = accountsList.indexOf(account)
                                                        val targetIndex = (currentIndex + if (dragAmount.y > 0) 1 else -1).coerceIn(0, accountsList.size - 1)
                                                        if (currentIndex != targetIndex) {
                                                            accountsList = accountsList.toMutableList().apply {
                                                                add(targetIndex, removeAt(currentIndex))
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

            // Bouton Café
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/elmuncho"))
                        context.startActivity(intent)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("☕", style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.home_kofi), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showAddDialog) {
        val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
        AccountFormDialog(
            title = stringResource(R.string.account_add_title),
            initialAccount = null,
            allAccounts = allAccounts,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, bankName, initialBalance, type, isJoint, color, memberEmail, _, linkedAccountId ->
                viewModel.addAccount(name, bankName, initialBalance, type, isJoint, color, linkedAccountId) { newAccountId ->
                    if (isJoint && memberEmail.isNotBlank()) {
                        viewModel.addMemberToAccount(newAccountId, memberEmail) { _, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    showAddDialog = false
                }
            }
        )
    }

    accountToEdit?.let { account ->
        val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
        AccountFormDialog(
            title = stringResource(R.string.account_edit_title),
            initialAccount = account,
            allAccounts = allAccounts,
            onDismiss = { accountToEdit = null },
            onConfirm = { name, bankName, initialBalance, type, isJoint, color, memberEmail, _, linkedAccountId ->
                viewModel.updateAccount(account.id, name, bankName, initialBalance, type, isJoint, color, linkedAccountId) {
                    if (isJoint && memberEmail.isNotBlank()) {
                        viewModel.addMemberToAccount(account.id, memberEmail) { _, message ->
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
}

@Composable
fun AccountCard(
    account: Account,
    currentBalance: Double,
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
                    val cryptoSymbol = "BTC" // Peut être rendu dynamique si stocké dans l'objet Account
                    Text(
                        text = String.format(Locale.US, "%.4f %s", currentBalance, cryptoSymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )

                    // Calcul de la conversion avec le taux récupéré en direct
                    val rate = cryptoRates[cryptoSymbol] ?: 0.0
                    val estimatedEuro = currentBalance * rate

                    Text(
                        text = String.format(Locale.US, "≈ %.2f %s", estimatedEuro, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
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
    var selectedCryptoSymbol by remember { mutableStateOf("BTC") }
    var linkedAccountId by remember { mutableStateOf(initialAccount?.linkedAccountId) }
    var expandedLinkedAccount by remember { mutableStateOf(false) }

    var isJoint by remember { mutableStateOf(initialAccount?.let { it.isJoint || it.members.size > 1 } ?: false) }
    var selectedColor by remember { mutableStateOf(if (!initialAccount?.color.isNullOrBlank()) initialAccount!!.color else "#2196F3") }
    var memberEmail by remember { mutableStateOf("") }

    val availableColors = listOf("#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4")
    val supportedCryptos = listOf("BTC" to "Bitcoin", "ETH" to "Ethereum", "SOL" to "Solana", "USDT" to "USDT", "ADA" to "Cardano", "XRP" to "XRP")

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

                Text(stringResource(R.string.account_type_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val accountTypes = listOf(
                        "CHECKING" to stringResource(R.string.account_type_checking),
                        "SAVINGS" to stringResource(R.string.account_type_savings),
                        "LIVRET_A" to stringResource(R.string.account_type_livret_a),
                        "CREDIT" to stringResource(R.string.account_type_credit),
                        "CRYPTO" to stringResource(R.string.account_type_crypto)
                    )
                    accountTypes.chunked(3).forEach { rowTypes ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { (typeKey, typeLabel) ->
                                FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = selectedType == typeKey,
                                    onClick = { selectedType = typeKey },
                                    label = { Text(typeLabel, maxLines = 1) }
                                )
                            }
                        }
                    }
                }

                if (selectedType == "CRYPTO") {
                    Text(stringResource(R.string.account_crypto_asset), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        supportedCryptos.chunked(3).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { (symbol, label) ->
                                    FilterChip(modifier = Modifier.weight(1f), selected = selectedCryptoSymbol == symbol, onClick = { selectedCryptoSymbol = symbol }, label = { Text(label, maxLines = 1) })
                                }
                            }
                        }
                    }
                }

                if (selectedType == "CREDIT") {
                    Text(stringResource(R.string.tx_source_account_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = expandedLinkedAccount,
                        onExpandedChange = { expandedLinkedAccount = !expandedLinkedAccount }
                    ) {
                        val selectedAcc = allAccounts.find { it.id == linkedAccountId }
                        OutlinedTextField(
                            value = selectedAcc?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sélectionner un compte") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
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

                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text(if (selectedType == "CRYPTO") stringResource(R.string.account_crypto_quantity, selectedCryptoSymbol) else stringResource(R.string.account_initial_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.account_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.forEach { hexColor ->
                        val colorInt = android.graphics.Color.parseColor(hexColor)
                        FilterChip(selected = selectedColor == hexColor, onClick = { selectedColor = hexColor }, label = { Text("●", color = Color(colorInt), style = MaterialTheme.typography.titleLarge) })
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isJoint, onCheckedChange = { isJoint = it })
                    Spacer(modifier = Modifier.width(8.dp))
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
                enabled = accountName.isNotBlank() && initialBalanceText.toDoubleOrNull() != null,
                onClick = {
                    val initialBalance = initialBalanceText.toDoubleOrNull() ?: 0.0
                    val finalCryptoSymbol = if (selectedType == "CRYPTO") selectedCryptoSymbol else null
                    onConfirm(accountName.trim(), bankName.trim(), initialBalance, selectedType, isJoint, selectedColor, memberEmail.trim(), finalCryptoSymbol, linkedAccountId)
                }
            ) { Text(stringResource(R.string.account_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
    )
}