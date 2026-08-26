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
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.utils.parseHexColor
import com.muncho.kaptal.viewmodel.AccountsUiState
import com.muncho.kaptal.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel,
    initialTypeKey: String? = null,
    onAccountAdded: (Account, String?) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }
    var initialInvestmentText by remember { mutableStateOf("") }
    var savingsRateText by remember { mutableStateOf("") }

    val typeChecking = stringResource(Res.string.account_type_checking)
    val typeSavings = stringResource(Res.string.account_type_savings)
    val typeLivretA = stringResource(Res.string.account_type_livret_a)
    val typeSavingsDaily = "Rémunéré (Quotidien)"
    val typeBrokerage = "Courtage"
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

    var selectedTypeKey by remember { mutableStateOf(initialTypeKey ?: "CHECKING") }
    
    var nameError by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf("#2196F3") }
    var isJoint by remember { mutableStateOf(false) }
    var memberEmail by remember { mutableStateOf("") }
    var linkedAccountId by remember { mutableStateOf<String?>(null) }
    var cryptoSymbol by remember { mutableStateOf("") }

    val availableColors = listOf(
        "#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0",
        "#00BCD4", "#795548", "#607D8B", "#F44336", "#FFEB3B"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.add_account_title), fontWeight = FontWeight.Bold) },
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
                label = { Text(if (selectedTypeKey == "CRYPTO") "Plateforme (ex: Binance)" else stringResource(Res.string.account_bank_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedTypeKey == "CRYPTO") {
                OutlinedTextField(
                    value = cryptoSymbol,
                    onValueChange = { cryptoSymbol = it.uppercase().trim() },
                    label = { Text("Symbole (ex: BTC, ETH)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("BTC") }
                )
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
                    label = { Text(if (selectedTypeKey == "CRYPTO") "Quantité initialement détenue" else stringResource(Res.string.account_initial_balance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedTypeKey == "CRYPTO") {
                OutlinedTextField(
                    value = initialInvestmentText,
                    onValueChange = { initialInvestmentText = it },
                    label = { Text("Coût d'achat total du solde initial (€)") },
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
                    label = { Text("Taux d'intérêt annuel (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: 4.0") }
                )
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
                        val account = Account(
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
                        onAccountAdded(account, if (isJoint) memberEmail.trim() else null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.add_account_create_button))
            }
        }
    }
}
