package com.example.kaptal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onAccountAdded: (String, String, Double, String, String) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }

    // Devises
    val currencies = listOf("EUR (€)", "USD ($)", "GBP (£)", "CHF (₣)", "BTC", "ETH")
    var currencyExpanded by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(currencies[0]) }

    // Types de comptes
    val accountTypes = listOf("Courant", "Épargne", "Joint", "Crédit", "Crypto", "Investissement", "Autre")
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(accountTypes[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ajouter un compte",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. Nom du compte
        OutlinedTextField(
            value = accountName,
            onValueChange = { accountName = it },
            label = { Text("Nom du compte (ex: Compte Perso)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Établissement / Organisme
        OutlinedTextField(
            value = institution,
            onValueChange = { institution = it },
            label = { Text("Établissement (ex: Crédit Agricole, Binance...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Solde initial
        OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it },
            label = { Text("Solde initial") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Choix de la Devise
        ExposedDropdownMenuBox(
            expanded = currencyExpanded,
            onExpandedChange = { currencyExpanded = !currencyExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedCurrency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Devise") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = currencyExpanded,
                onDismissRequest = { currencyExpanded = false }
            ) {
                currencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            selectedCurrency = currency
                            currencyExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Choix du Type de compte
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = !typeExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type de compte") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                accountTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectedType = type
                            typeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bouton Enregistrer
        Button(
            onClick = {
                val balance = balanceText.toDoubleOrNull() ?: 0.0
                if (accountName.isNotEmpty()) {
                    onAccountAdded(accountName, institution, balance, selectedCurrency, selectedType)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Enregistrer le compte", fontSize = 16.sp)
        }
    }
}