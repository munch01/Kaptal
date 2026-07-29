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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBackClick: () -> Unit,
    onAccountAdded: (Account) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }

    // Types de comptes et devises
    val accountTypes = listOf("Compte Courant", "Livret d'Épargne", "Carte de Crédit", "Investissement", "Espèces")
    var selectedType by remember { mutableStateOf(accountTypes[0]) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val currencies = listOf("EUR (€)", "USD ($)", "GBP (£)", "CHF (CHF)")
    var selectedCurrency by remember { mutableStateOf(currencies[0]) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un compte", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
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
                label = { Text("Nom du compte") },
                isError = nameError,
                supportingText = { if (nameError) Text("Le nom du compte est requis") },
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
                label = { Text("Solde initial") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = balanceError,
                supportingText = { if (balanceError) Text("Veuillez entrer un montant valide") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Type de compte (Dropdown)
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type de compte") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

            // Devise (Dropdown)
            ExposedDropdownMenuBox(
                expanded = currencyDropdownExpanded,
                onExpandedChange = { currencyDropdownExpanded = !currencyDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCurrency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Devise") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = currencyDropdownExpanded,
                    onDismissRequest = { currencyDropdownExpanded = false }
                ) {
                    currencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text(currency) },
                            onClick = {
                                selectedCurrency = currency
                                currencyDropdownExpanded = false
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
                        val newAccount = Account(
                            id = System.currentTimeMillis().toString(),
                            name = accountName.trim(),
                            balance = parsedBalance ?: 0.0,
                            currency = selectedCurrency
                        )
                        onAccountAdded(newAccount)
                        onBackClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Créer le compte")
            }
        }
    }
}