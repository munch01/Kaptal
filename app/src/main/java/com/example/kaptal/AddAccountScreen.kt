package com.example.kaptal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kaptal.model.Account


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBackClick: () -> Unit,
    onAccountAdded: (
        name: String,
        bankName: String,
        initialBalance: Double,
        type: String,
        isJoint: Boolean,
        color: String
    ) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }

    // Types de comptes épurés (uniquement Courant, Épargne, Livret A et Espèces)
    val accountTypes = listOf(
        stringResource(R.string.account_type_checking),
        stringResource(R.string.account_type_savings),
        stringResource(R.string.account_type_livret_a),
        stringResource(R.string.account_type_cash)
    )
    var selectedType by remember { mutableStateOf(accountTypes[0]) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_label)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                onValueChange = {
                    accountName = it
                    if (it.isNotBlank()) nameError = false
                },
                label = { Text(stringResource(R.string.account_name_label)) },
                isError = nameError,
                supportingText = { if (nameError) Text(stringResource(R.string.add_account_name_error)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Nom de la banque
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text(stringResource(R.string.add_account_bank_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Solde initial
            OutlinedTextField(
                value = initialBalance,
                onValueChange = {
                    initialBalance = it
                    if (it.isNotBlank()) balanceError = false
                },
                label = { Text(stringResource(R.string.account_initial_balance)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = balanceError,
                supportingText = { if (balanceError) Text(stringResource(R.string.add_account_balance_error)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Type de compte (Dropdown épuré)
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.account_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )

                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    accountTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedType = type
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bouton de validation
            Button(
                onClick = {
                    val parsedBalance = initialBalance.replace(",", ".").toDoubleOrNull()

                    nameError = accountName.isBlank()
                    balanceError = parsedBalance == null

                    if (!nameError && !balanceError) {
                        onAccountAdded(
                            accountName.trim(),
                            bankName.trim(),
                            parsedBalance ?: 0.0,
                            selectedType,
                            false,
                            "#2196F3"
                        )
                        onBackClick()
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