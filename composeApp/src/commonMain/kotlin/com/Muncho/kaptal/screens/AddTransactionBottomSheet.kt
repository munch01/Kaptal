package com.muncho.kaptal.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.model.CategoryFamily
import com.muncho.kaptal.model.Transaction
import com.muncho.kaptal.utils.DateTimeUtils
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    initialTransaction: Transaction? = null,
    accounts: List<Account> = emptyList(),
    currentAccountId: String = "",
    categories: List<CategoryFamily> = emptyList(),
    cryptoRates: Map<String, Double> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        familyCategory: String,
        subCategory: String,
        type: String,
        paymentMethod: String,
        date: Timestamp,
        isRecurring: Boolean,
        recurrenceInterval: String?,
        endDate: Timestamp?,
        sourceAccountId: String,
        targetAccountId: String?,
        investmentEur: Double?,
        feesPercent: Double?
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }

    val initialAbsAmount = initialTransaction?.amount?.let { if (it < 0) -it else it }
    var amountText by remember { mutableStateOf(initialAbsAmount?.let { if (it == 0.0) "" else it.toString() } ?: "") }
    
    var investmentEurText by remember { mutableStateOf(initialTransaction?.investmentEur?.toString() ?: "") }
    var feesPercentText by remember { mutableStateOf(initialTransaction?.feesPercent?.toString() ?: "0.0") }

    var type by remember { mutableStateOf(initialTransaction?.type ?: "EXPENSE") }

    val incomeCategories = listOf(
        "Salaire", "Remboursement santé", "Virement entre compte",
        "Virement divers", "Impôts", "Autre / Divers"
    )

    var familyCategory by remember {
        mutableStateOf(
            if (initialTransaction?.type == "INCOME") "Recettes"
            else initialTransaction?.familyCategory?.takeIf { it.isNotBlank() } ?: categories.firstOrNull()?.name ?: ""
        )
    }

    var subCategory by remember {
        mutableStateOf(
            if (initialTransaction?.type == "INCOME") initialTransaction.subCategory?.takeIf { it.isNotBlank() } ?: incomeCategories.first()
            else initialTransaction?.subCategory?.takeIf { it.isNotBlank() } ?: categories.firstOrNull()?.subCategories?.firstOrNull() ?: ""
        )
    }

    var expandedFamily by remember { mutableStateOf(false) }
    var expandedSubCategory by remember { mutableStateOf(false) }

    val paymentMethods = listOf("CB", "Espèces", "Virement", "Chèque")
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: "CB") }
    var expandedPayment by remember { mutableStateOf(false) }

    var targetAccountId by remember { mutableStateOf<String?>(initialTransaction?.targetAccountId) }
    var expandedTargetAccount by remember { mutableStateOf(false) }
    var isTransferIncoming by remember { mutableStateOf(initialTransaction?.let { it.amount > 0 } ?: false) }

    val platform = getPlatform()
    
    var selectedDate by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.date?.toInstant() ?: DateTimeUtils.now())
    }

    var isRecurring by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.isRecurring ?: false)
    }

    var endDate by remember {
        mutableStateOf<Instant?>(initialTransaction?.endDate?.toInstant())
    }

    var showExitConfirmation by remember { mutableStateOf(false) }
    val isDirty = title.isNotBlank() || (amountText.isNotBlank() && amountText != "0" && amountText != "0.0")

    val currentAccount = remember(currentAccountId, accounts) { accounts.find { it.id == currentAccountId } }
    val targetAcc = remember(targetAccountId, accounts) { accounts.find { it.id == targetAccountId } }
    val cryptoAccount = if (currentAccount?.type == "CRYPTO") currentAccount else if (targetAcc?.type == "CRYPTO") targetAcc else null
    val isCryptoInvolved = cryptoAccount != null
    val cryptoSymbol = cryptoAccount?.cryptoSymbol ?: "BTC"
    val rate = cryptoRates[cryptoSymbol] ?: 0.0

    var isManuallyEditingAmount by remember { mutableStateOf(false) }
    var isManuallyEditingInvestment by remember { mutableStateOf(false) }

    if (rate > 0 && type != "EXPENSE") {
        if (!isManuallyEditingInvestment && isManuallyEditingAmount) {
            val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
            val isCurrentCrypto = currentAccount?.type == "CRYPTO"
            if (isCurrentCrypto) {
                val calculated = amount * rate
                if (calculated != 0.0) investmentEurText = calculated.roundTo(2).toString()
            } else if (targetAcc?.type == "CRYPTO") {
                investmentEurText = amountText 
            }
        }
        
        if (!isManuallyEditingAmount && isManuallyEditingInvestment) {
            val invEur = investmentEurText.replace(",", ".").toDoubleOrNull() ?: 0.0
            val isCurrentCrypto = currentAccount?.type == "CRYPTO"
            if (isCurrentCrypto) {
                val calculated = invEur / rate
                if (calculated != 0.0) amountText = calculated.roundTo(6).toString()
            } else if (targetAcc?.type == "CRYPTO") {
                amountText = investmentEurText
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Abandonner ?") },
            text = { Text("Voulez-vous vraiment annuler la saisie de cette opération ?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onDismiss()
                }) {
                    Text("Oui, abandonner", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Continuer la saisie")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isDirty && initialTransaction == null) {
                showExitConfirmation = true
            } else {
                onDismiss()
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = if (initialTransaction == null) "Nouvelle opération" else "Modifier l'opération",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == "EXPENSE",
                        onClick = {
                            type = "EXPENSE"
                            familyCategory = categories.firstOrNull()?.name ?: ""
                            subCategory = categories.firstOrNull()?.subCategories?.firstOrNull() ?: ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("Dépense")
                    }
                    SegmentedButton(
                        selected = type == "INCOME",
                        onClick = {
                            type = "INCOME"
                            familyCategory = "Recettes"
                            subCategory = incomeCategories.first()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("Revenu")
                    }
                    SegmentedButton(
                        selected = type == "TRANSFER",
                        onClick = {
                            type = "TRANSFER"
                            familyCategory = "Virement"
                            subCategory = "Virement interne"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Virement")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Libellé") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { 
                        amountText = it
                        isManuallyEditingAmount = true
                        isManuallyEditingInvestment = false
                    },
                    label = { 
                        val isCurrentCrypto = currentAccount?.type == "CRYPTO"
                        Text(if (isCurrentCrypto) "Quantité ($cryptoSymbol)" else "Montant (€)") 
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (isCryptoInvolved && type != "EXPENSE") {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = investmentEurText,
                            onValueChange = { 
                                investmentEurText = it
                                isManuallyEditingInvestment = true
                                isManuallyEditingAmount = false
                            },
                            label = { Text("Coût (€)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = feesPercentText,
                            onValueChange = { feesPercentText = it },
                            label = { Text("Frais (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            if (type == "TRANSFER") {
                item {
                    val currentAccountName = currentAccount?.name ?: "Ce compte"
                    val targetAccountName = targetAcc?.name ?: "Autre compte"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Direction du virement", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val fromName = if (isTransferIncoming) targetAccountName else currentAccountName
                                val toName = if (isTransferIncoming) currentAccountName else targetAccountName
                                
                                Text(fromName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                
                                IconButton(onClick = { isTransferIncoming = !isTransferIncoming }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Inverser",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Text(toName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedTargetAccount,
                        onExpandedChange = { expandedTargetAccount = !expandedTargetAccount }
                    ) {
                        OutlinedTextField(
                            value = targetAcc?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Compte cible") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTargetAccount,
                            onDismissRequest = { expandedTargetAccount = false }
                        ) {
                            accounts.filter { it.id != currentAccountId }.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        targetAccountId = account.id
                                        expandedTargetAccount = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (type == "EXPENSE") {
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedFamily,
                        onExpandedChange = { expandedFamily = !expandedFamily }
                    ) {
                        OutlinedTextField(
                            value = familyCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Famille") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFamily,
                            onDismissRequest = { expandedFamily = false }
                        ) {
                            categories.forEach { family ->
                                DropdownMenuItem(
                                    text = { Text(family.name, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        familyCategory = family.name
                                        subCategory = family.subCategories.firstOrNull() ?: ""
                                        expandedFamily = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    val currentFamilyObj = categories.find { it.name == familyCategory }
                    val availableSubCategories = currentFamilyObj?.subCategories ?: emptyList()

                    ExposedDropdownMenuBox(
                        expanded = expandedSubCategory,
                        onExpandedChange = { expandedSubCategory = !expandedSubCategory }
                    ) {
                        OutlinedTextField(
                            value = subCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sous-catégorie") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSubCategory,
                            onDismissRequest = { expandedSubCategory = false }
                        ) {
                            availableSubCategories.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        subCategory = sub
                                        expandedSubCategory = false
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedSubCategory,
                        onExpandedChange = { expandedSubCategory = !expandedSubCategory }
                    ) {
                        OutlinedTextField(
                            value = subCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Catégorie") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSubCategory,
                            onDismissRequest = { expandedSubCategory = false }
                        ) {
                            incomeCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        subCategory = cat
                                        expandedSubCategory = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedPayment,
                    onExpandedChange = { expandedPayment = !expandedPayment }
                ) {
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode de paiement") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPayment,
                        onDismissRequest = { expandedPayment = false }
                    ) {
                        paymentMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method) },
                                onClick = {
                                    paymentMethod = method
                                    expandedPayment = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        platform.pickDate(selectedDate.toEpochMilliseconds()) {
                            selectedDate = Instant.fromEpochMilliseconds(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Date : ${DateTimeUtils.formatDate(selectedDate, "dd/MM/yyyy")}")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Opération récurrente")
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                }
            }

            if (isRecurring) {
                item {
                    OutlinedButton(
                        onClick = {
                            val baseDate = endDate ?: DateTimeUtils.now()
                            platform.pickDate(baseDate.toEpochMilliseconds()) {
                                endDate = Instant.fromEpochMilliseconds(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val safeEndDate = endDate
                        val dateString = if (safeEndDate != null) DateTimeUtils.formatDate(safeEndDate, "dd/MM/yyyy") else ""
                        Text(text = if (safeEndDate != null) "Date de fin : $dateString" else "Pas de date de fin")
                    }
                }

                if (endDate != null) {
                    item {
                        TextButton(
                            onClick = { endDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Supprimer la date de fin")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val rawAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val rawInvEur = investmentEurText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        
                        val finalAmount = when(type) {
                            "EXPENSE" -> -abs(rawAmount)
                            "TRANSFER" -> if (isTransferIncoming) abs(rawAmount) else -abs(rawAmount)
                            else -> abs(rawAmount)
                        }
                        
                        val finalFamily = when(type) {
                            "INCOME" -> "Recettes"
                            "TRANSFER" -> "Virement"
                            else -> familyCategory
                        }

                        if (title.isNotBlank() && (rawAmount != 0.0 || rawInvEur != 0.0)) {
                            val safeEndDate = endDate
                            val finalSourceId = if (type == "TRANSFER" && isTransferIncoming) targetAccountId ?: "" else currentAccountId
                            val finalTargetId = if (type == "TRANSFER" && isTransferIncoming) currentAccountId else targetAccountId

                            onSave(
                                title,
                                finalAmount,
                                finalFamily,
                                subCategory,
                                type,
                                paymentMethod,
                                selectedDate.toTimestamp(),
                                isRecurring,
                                if (isRecurring) "MONTHLY" else null,
                                safeEndDate?.toTimestamp(),
                                finalSourceId,
                                finalTargetId,
                                if (rawInvEur != 0.0) rawInvEur else null,
                                feesPercentText.toDoubleOrNull()
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (initialTransaction == null) "Enregistrer" else "Modifier")
                }
            }
        }
    }
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(this.epochSeconds, this.nanosecondsOfSecond)
private fun Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(this.seconds, this.nanoseconds)

private fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}
