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
    var bankName by remember { mutableStateOf("") } // Ajouté si vous en avez besoin
    var initialBalance by remember { mutableStateOf("") }

    // Types de comptes
    val accountTypes = listOf("Compte Courant", "Livret d'Épargne", "Carte de Crédit", "Investissement", "Espèces")
    var selectedType by remember { mutableStateOf(accountTypes[0]) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

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

            // Nom de la banque (optionnel mais utile selon votre modèle)
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text("Nom de la banque (ex: Boursorama, Revolut...)") },
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
                            false, // isJoint par défaut
                            "#2196F3" // Couleur par défaut
                        )
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