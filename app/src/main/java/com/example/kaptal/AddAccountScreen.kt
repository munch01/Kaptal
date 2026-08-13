package com.Muncho.kaptal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.Muncho.kaptal.model.Account


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

    val typeChecking = stringResource(R.string.account_type_checking)
    val typeSavings = stringResource(R.string.account_type_savings)
    val typeLivretA = stringResource(R.string.account_type_livret_a)
    val typeCredit = stringResource(R.string.account_type_credit)
    val typeCrypto = stringResource(R.string.account_type_crypto)
    val typeCash = stringResource(R.string.account_type_cash)

    // Types de comptes complets
    val accountTypes = listOf(typeChecking, typeSavings, typeLivretA, typeCredit, typeCrypto, typeCash)
    var selectedType by remember { mutableStateOf(accountTypes[0]) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf(false) }

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

        Scaffold(
            containerColor = Color.Transparent,
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
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
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
                            val typeKey = when (selectedType) {
                                typeSavings -> "SAVINGS"
                                typeLivretA -> "LIVRET_A"
                                typeCredit -> "CREDIT"
                                typeCrypto -> "CRYPTO"
                                typeCash -> "CASH"
                                else -> "CHECKING"
                            }
                            onAccountAdded(
                                accountName.trim(),
                                bankName.trim(),
                                parsedBalance ?: 0.0,
                                typeKey,
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
}
