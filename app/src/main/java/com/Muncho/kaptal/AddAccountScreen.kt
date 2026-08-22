package com.Muncho.kaptal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.model.Account
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel = viewModel(),
    initialTypeKey: String? = null,
    onAccountAdded: (
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String,
        linkedAccountId: String?,
        memberEmail: String?,
        cryptoSymbol: String? // Nouveau paramètre
    ) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }

    val typeChecking = stringResource(R.string.account_type_checking)
    val typeSavings = stringResource(R.string.account_type_savings)
    val typeLivretA = stringResource(R.string.account_type_livret_a)
    val typeCredit = stringResource(R.string.account_type_credit)
    val typeCrypto = stringResource(R.string.account_type_crypto)

    val accountTypes = listOf(
        "CHECKING" to typeChecking,
        "SAVINGS" to typeSavings,
        "LIVRET_A" to typeLivretA,
        "CREDIT" to typeCredit,
        "CRYPTO" to typeCrypto
    )
    
    // Logique de filtrage des types selon le contexte
    val filteredTypes = remember(initialTypeKey) {
        when (initialTypeKey) {
            "SAVINGS" -> listOf("SAVINGS" to typeSavings, "LIVRET_A" to typeLivretA)
            "CHECKING" -> listOf("CHECKING" to typeChecking)
            "CREDIT" -> listOf("CREDIT" to typeCredit)
            "CRYPTO" -> listOf("CRYPTO" to typeCrypto)
            else -> accountTypes
        }
    }

    var selectedTypeKey by remember { mutableStateOf(initialTypeKey ?: "CHECKING") }
    
    // Si on arrive sur Épargne, on force le choix entre Épargne et Livret A
    LaunchedEffect(initialTypeKey) {
        if (initialTypeKey == "SAVINGS") {
            selectedTypeKey = "SAVINGS"
        }
    }

    var nameError by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf("#2196F3") }
    var isJoint by remember { mutableStateOf(false) }
    var memberEmail by remember { mutableStateOf("") }
    var linkedAccountId by remember { mutableStateOf<String?>(null) }
    var expandedLinkedAccount by remember { mutableStateOf(false) }
    var cryptoSymbol by remember { mutableStateOf("") }

    val availableColors = listOf(
        "#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0",
        "#00BCD4", "#795548", "#607D8B", "#F44336", "#FFEB3B"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nom du compte
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it; if (it.isNotBlank()) nameError = false },
                label = { Text(stringResource(R.string.account_name_label)) },
                isError = nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Nom de la banque
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text(if (selectedTypeKey == "CRYPTO") "Plateforme (ex: Binance)" else stringResource(R.string.account_bank_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // --- CHOIX CRYPTO ---
            if (selectedTypeKey == "CRYPTO") {
                var expandedCrypto by remember { mutableStateOf(false) }
                val popular = viewModel.popularCryptos
                
                ExposedDropdownMenuBox(
                    expanded = expandedCrypto,
                    onExpandedChange = { expandedCrypto = !expandedCrypto }
                ) {
                    OutlinedTextField(
                        value = cryptoSymbol,
                        onValueChange = { cryptoSymbol = it.uppercase().trim() },
                        label = { Text("Symbole (ex: BTC, ETH)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        placeholder = { Text("BTC") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCrypto) }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedCrypto,
                        onDismissRequest = { expandedCrypto = false }
                    ) {
                        popular.forEach { (symbol, name) ->
                            DropdownMenuItem(
                                text = { 
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(symbol, fontWeight = FontWeight.Bold)
                                        Text(name, color = Color.Gray)
                                    }
                                },
                                onClick = {
                                    cryptoSymbol = symbol
                                    expandedCrypto = false
                                }
                            )
                        }
                    }
                }
            }

            // Type de compte (Tuiles) - Masqué si un seul type possible
            if (filteredTypes.size > 1) {
                Text(stringResource(R.string.account_type_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredTypes.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedTypeKey == key,
                            onClick = { selectedTypeKey = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Compte de prélèvement (Uniquement pour CREDIT)
            if (selectedTypeKey == "CREDIT") {
                val accountsState by viewModel.uiState.collectAsState()
                val allAccounts = if (accountsState is AccountsUiState.Success) (accountsState as AccountsUiState.Success).accounts else emptyList()

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
                        allAccounts.filter { it.type != "CREDIT" }.forEach { acc ->
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

            // Solde initial (Caché pour les crédits)
            if (selectedTypeKey != "CREDIT") {
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it; if (it.isNotBlank()) balanceError = false },
                    label = { Text(stringResource(R.string.account_initial_balance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = balanceError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Couleurs
            Text(stringResource(R.string.account_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(
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

            // Partagé
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bouton de validation
            Button(
                onClick = {
                    val parsedBalance = if (selectedTypeKey == "CREDIT") 0.0 else initialBalance.replace(",", ".").toDoubleOrNull() ?: 0.0
                    nameError = accountName.isBlank()
                    balanceError = selectedTypeKey != "CREDIT" && initialBalance.isBlank()

                    if (!nameError && (!balanceError || selectedTypeKey == "CREDIT")) {
                        onAccountAdded(
                            accountName.trim(),
                            bankName.trim(),
                            parsedBalance,
                            selectedTypeKey,
                            isJoint,
                            selectedColor,
                            linkedAccountId,
                            if (isJoint) memberEmail.trim() else null,
                            if (selectedTypeKey == "CRYPTO") cryptoSymbol else null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_account_create_button))
            }
        }
    }
}
