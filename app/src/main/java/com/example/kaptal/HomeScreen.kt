package com.example.kaptal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptal.model.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedCurrency by viewModel.appCurrency.collectAsState() // <-- Observation de la devise globale harmonisée

    // Gestion des dialogues (Ajout vs Édition)
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Comptes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? Activity)?.finish()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Quitter l'application"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/munch01/Kaptal/issues"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Feedback,
                            contentDescription = "Signaler un bug / Amélioration"
                        )
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres"
                        )
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
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un compte")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is AccountsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is AccountsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadAccounts() }) {
                            Text("Réessayer")
                        }
                    }
                }

                is AccountsUiState.Success -> {
                    if (state.accounts.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Aucun compte bancaire configuré.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Appuyez sur + pour en ajouter un.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
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
                            items(
                                items = accountsList,
                                key = { account: Account -> account.id }
                            ) { account ->
                                AccountCard(
                                    account = account,
                                    currency = selectedCurrency,
                                    onClick = { onAccountClick(account) },
                                    onEditClick = { accountToEdit = account },
                                    onDeleteClick = { accountToDelete = account },
                                    modifier = Modifier
                                        .animateItem()
                                        .pointerInput(accountsList) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { },
                                                onDragEnd = {
                                                    viewModel.updateAccountsOrder(accountsList)
                                                },
                                                onDragCancel = {
                                                    viewModel.updateAccountsOrder(accountsList)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val currentIndex = accountsList.indexOf(account)
                                                    val targetIndex = (currentIndex + if (dragAmount.y > 0) 1 else -1)
                                                        .coerceIn(0, accountsList.size - 1)

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

            // --- BOUTON "OFFRIR UN CAFÉ" EN BAS À GAUCHE ---
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
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "☕",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Café",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // --- DIALOGUE D'AJOUT DE COMPTE ---
    if (showAddDialog) {
        AccountFormDialog(
            title = "Ajouter un compte bancaire",
            initialAccount = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, bankName, initialBalance, type, isJoint, color, memberEmail ->
                viewModel.addAccount(name, bankName, initialBalance, type, isJoint, color) { newAccountId ->
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

    // --- DIALOGUE DE MODIFICATION DE COMPTE ---
    accountToEdit?.let { account ->
        AccountFormDialog(
            title = "Modifier le compte",
            initialAccount = account,
            onDismiss = { accountToEdit = null },
            onConfirm = { name, bankName, initialBalance, type, isJoint, color, memberEmail ->
                viewModel.updateAccount(account.id, name, bankName, initialBalance, type, isJoint, color) {
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

    // --- DIALOGUE DE CONFIRMATION DE SUPPRESSION ---
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Supprimer ce compte ?") },
            text = { Text("Voulez-vous vraiment supprimer le compte « ${account.name} » ? Ses données seront effacées de Firestore.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        accountToDelete = null
                    }
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun AccountCard(
    account: Account,
    currency: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountColor = try {
        Color(android.graphics.Color.parseColor(account.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp, 40.dp)
                    .background(accountColor, shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (account.bankName.isNotBlank()) {
                    Text(
                        text = account.bankName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = when (account.type) {
                            "SAVINGS" -> "Épargne"
                            "CREDIT" -> "Crédit"
                            else -> "Courant"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account.isJoint) {
                        Text(
                            text = "• Partagé",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val balanceColor = if (account.initialBalance >= 0) {
                    Color(0xFF2E7D32)
                } else {
                    MaterialTheme.colorScheme.error
                }

                Text(
                    text = String.format("%.2f %s", account.initialBalance, currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier le compte",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer le compte",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun AccountFormDialog(
    title: String,
    initialAccount: Account?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, bankName: String, initialBalance: Double, type: String, isJoint: Boolean, color: String, memberEmail: String) -> Unit
) {
    var accountName by remember { mutableStateOf(initialAccount?.name ?: "") }
    var bankName by remember { mutableStateOf(initialAccount?.bankName ?: "") }
    var initialBalanceText by remember { mutableStateOf(initialAccount?.initialBalance?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(initialAccount?.type ?: "CHECKING") }
    var isJoint by remember {
        mutableStateOf(
            initialAccount?.let { it.isJoint || it.members.size > 1 } ?: false
        )
    }
    var selectedColor by remember { mutableStateOf(initialAccount?.color?.takeIf { it.isNotBlank() } ?: "#2196F3") }
    var memberEmail by remember { mutableStateOf("") }

    val availableColors = listOf("#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4")

    // --- LISTE DES 3 TYPES DE COMPTES SOUHAITÉS ---
    val accountTypes = listOf(
        "CHECKING" to "Courant",
        "SAVINGS" to "Épargne",
        "CREDIT" to "Crédit"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Nom du compte") },
                    placeholder = { Text("Ex: Compte Perso, Livret A...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Établissement bancaire") },
                    placeholder = { Text("Ex: Crédit Agricole, BoursoBank...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text("Solde initial") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Couleur du compte", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableColors.forEach { hexColor ->
                        val colorInt = android.graphics.Color.parseColor(hexColor)
                        FilterChip(
                            selected = selectedColor == hexColor,
                            onClick = { selectedColor = hexColor },
                            label = { Text("●", color = Color(colorInt), style = MaterialTheme.typography.titleLarge) }
                        )
                    }
                }

                Text("Type de compte", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountTypes.forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(typeLabel, maxLines = 1) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isJoint,
                        onCheckedChange = { isJoint = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compte partagé / joint")
                }

                if (isJoint) {
                    if (initialAccount != null && initialAccount.members.size > 1) {
                        Text(
                            text = "💡 Ce compte est déjà partagé avec d'autres utilisateurs (${initialAccount.members.size} membres).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = memberEmail,
                        onValueChange = { memberEmail = it },
                        label = { Text("Ajouter un co-titulaire (E-mail)") },
                        placeholder = { Text("Ex: jessica@example.com") },
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
                    onConfirm(accountName.trim(), bankName.trim(), initialBalance, selectedType, isJoint, selectedColor, memberEmail.trim())
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}