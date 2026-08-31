package com.muncho.kaptal.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.utils.parseHexColor
import com.muncho.kaptal.viewmodel.AccountsUiState
import com.muncho.kaptal.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel,
    initialTypeKey: String? = null,
    accountToEdit: Account? = null,
    onAccountAdded: (Account, String?) -> Unit
) {
    val localFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    var accountName by remember { mutableStateOf(accountToEdit?.name ?: "") }
    var bankName by remember { mutableStateOf(accountToEdit?.bankName ?: "") }
    var initialBalanceText by remember { mutableStateOf(accountToEdit?.initialBalance?.toString() ?: "") }
    var initialInvestmentText by remember { mutableStateOf(accountToEdit?.initialInvestmentEur?.toString() ?: "") }
    var savingsRateText by remember { mutableStateOf(accountToEdit?.savingsRate?.toString() ?: "") }

    val typeChecking = stringResource(Res.string.account_type_checking)
    val typeSavings = stringResource(Res.string.account_type_savings)
    val typeLivretA = stringResource(Res.string.account_type_livret_a)
    val typeSavingsDaily = stringResource(Res.string.account_type_remunerated_daily)
    val typeBrokerage = stringResource(Res.string.account_type_brokerage)
    val typeCredit = stringResource(Res.string.account_type_credit)
    val typeCrypto = stringResource(Res.string.account_type_crypto)

    val accountTypes = listOf(
        "CHECKING" to typeChecking,
        "SAVINGS" to typeSavings,
        "LIVRET_A" to typeLivretA,
        "SAVINGS_DAILY" to typeSavingsDaily,
        "BROKERAGE" to typeBrokerage,
        "CREDIT" to typeCredit,
        "CRYPTO" to typeCrypto
    )
    
    val filteredTypes = remember(initialTypeKey) {
        when (initialTypeKey) {
            "SAVINGS" -> listOf(
                "SAVINGS" to typeSavings, 
                "LIVRET_A" to typeLivretA, 
                "SAVINGS_DAILY" to typeSavingsDaily, 
                "BROKERAGE" to typeBrokerage
            )
            "CHECKING" -> listOf("CHECKING" to typeChecking)
            "CREDIT" -> listOf("CREDIT" to typeCredit)
            "CRYPTO" -> listOf("CRYPTO" to typeCrypto)
            else -> accountTypes
        }
    }

    var selectedTypeKey by remember { mutableStateOf(accountToEdit?.type ?: initialTypeKey ?: "CHECKING") }
    
    var nameError by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(accountToEdit?.color ?: "#2196F3") }
    var isJoint by remember { mutableStateOf(accountToEdit?.isJoint ?: false) }
    var memberEmail by remember { mutableStateOf("") }
    var linkedAccountId by remember { mutableStateOf(accountToEdit?.linkedAccountId) }
    var cryptoSymbol by remember { mutableStateOf(accountToEdit?.cryptoSymbol ?: "") }
    var isLoading by remember { mutableStateOf(false) }

    val availableColors = listOf(
        "#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0",
        "#00BCD4", "#795548", "#607D8B", "#F44336", "#FFEB3B"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (accountToEdit != null) stringResource(Res.string.account_edit_title) else stringResource(Res.string.add_account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it; if (it.isNotBlank()) nameError = false },
                label = { Text(stringResource(Res.string.account_name_label)) },
                isError = nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text(if (selectedTypeKey == "CRYPTO") stringResource(Res.string.account_platform_label) else stringResource(Res.string.account_bank_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedTypeKey == "CRYPTO") {
                var expandedCrypto by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expandedCrypto,
                    onExpandedChange = { expandedCrypto = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = cryptoSymbol,
                        onValueChange = { cryptoSymbol = it.uppercase().trim() },
                        label = { Text(stringResource(Res.string.account_crypto_symbol_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCrypto) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                        placeholder = { Text("BTC") }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedCrypto,
                        onDismissRequest = { expandedCrypto = false }
                    ) {
                        viewModel.popularCryptos.forEach { (sym, name) ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(sym, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                },
                                onClick = {
                                    cryptoSymbol = sym
                                    expandedCrypto = false
                                }
                            )
                        }
                    }
                }
            }

            if (filteredTypes.size > 1) {
                Text(stringResource(Res.string.account_type_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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

            if (selectedTypeKey != "CREDIT") {
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text(if (selectedTypeKey == "CRYPTO") stringResource(Res.string.account_crypto_qty_label) else stringResource(Res.string.account_initial_balance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedTypeKey == "CRYPTO") {
                OutlinedTextField(
                    value = initialInvestmentText,
                    onValueChange = { initialInvestmentText = it },
                    label = { Text(stringResource(Res.string.account_crypto_cost_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: 1250.50") }
                )
            }

            if (selectedTypeKey == "SAVINGS_DAILY" || selectedTypeKey == "BROKERAGE") {
                OutlinedTextField(
                    value = savingsRateText,
                    onValueChange = { savingsRateText = it },
                    label = { Text(stringResource(Res.string.account_rate_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: 4.0") }
                )
            }

            if (selectedTypeKey == "CREDIT") {
                val uiState by viewModel.uiState.collectAsState()
                val accounts = (uiState as? com.muncho.kaptal.viewmodel.AccountsUiState.Success)?.accounts ?: emptyList()
                val checkingAccounts = accounts.filter { it.type == "CHECKING" }
                
                var expanded by remember { mutableStateOf(false) }
                val selectedAccount = checkingAccounts.find { it.id == linkedAccountId }

                Text(stringResource(Res.string.account_credit_linked_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: stringResource(Res.string.account_credit_select_hint),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        checkingAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    linkedAccountId = acc.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Text(stringResource(Res.string.account_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableColors.forEach { hexColor ->
                    FilterChip(
                        selected = selectedColor == hexColor,
                        onClick = { selectedColor = hexColor },
                        label = { Text("●", color = parseHexColor(hexColor), style = MaterialTheme.typography.titleLarge) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isJoint, onCheckedChange = { isJoint = it })
                Text(stringResource(Res.string.account_shared_checkbox))
            }

            if (isJoint) {
                OutlinedTextField(
                    value = memberEmail,
                    onValueChange = { memberEmail = it },
                    label = { Text(stringResource(Res.string.account_add_member_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val parsedBalance = initialBalanceText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    nameError = accountName.isBlank()
                    if (!nameError) {
                        localFocusManager.clearFocus() // Ferme le clavier avant de naviguer
                        val account = (accountToEdit ?: Account()).copy(
                            name = accountName.trim(),
                            bankName = bankName.trim(),
                            initialBalance = parsedBalance,
                            type = selectedTypeKey,
                            isJoint = isJoint,
                            color = selectedColor,
                            linkedAccountId = linkedAccountId,
                            cryptoSymbol = if (selectedTypeKey == "CRYPTO") cryptoSymbol else null,
                            initialInvestmentEur = if (selectedTypeKey == "CRYPTO") initialInvestmentText.replace(",", ".").toDoubleOrNull() else null,
                            savingsRate = savingsRateText.replace(",", ".").toDoubleOrNull()
                        )
                        isLoading = true
                        val finalMemberEmail = if (isJoint && memberEmail.isNotBlank()) memberEmail.trim().lowercase() else null
                        onAccountAdded(account, finalMemberEmail)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (accountToEdit != null) stringResource(Res.string.account_save) else stringResource(Res.string.add_account_create_button))
                }
            }
        }
    }
}
