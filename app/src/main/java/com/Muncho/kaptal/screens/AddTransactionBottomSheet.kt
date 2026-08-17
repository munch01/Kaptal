package com.Muncho.kaptal.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.Muncho.kaptal.LocalActivity
import com.Muncho.kaptal.R
import com.Muncho.kaptal.findActivity
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.CategoryFamily
import com.Muncho.kaptal.model.Transaction
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    initialTransaction: Transaction? = null,
    accounts: List<Account> = emptyList(),
    currentAccountId: String = "",
    categories: List<CategoryFamily> = emptyList(),
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
        investmentEur: Double? // Nouveau paramètre
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }

    val initialAbsAmount = initialTransaction?.amount?.let { if (it < 0) -it else it }
    var amountText by remember { mutableStateOf(initialAbsAmount?.let { if (it == 0.0) "" else it.toString() } ?: "") }
    
    var investmentEurText by remember { mutableStateOf(initialTransaction?.investmentEur?.toString() ?: "") }

    var type by remember { mutableStateOf(initialTransaction?.type ?: "EXPENSE") }

    // --- GESTION DES FAMILLES ET SOUS-CATÉGORIES POUR LES DÉPENSES ---
    val incomeCategories = listOf(
        "Salaire", "Remboursement santé", "Virement entre compte",
        "Virement divers", "Impôts", "Autre / Divers"
    )

    // Initialisation intelligente selon l'opération existante ou par défaut
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

    val paymentMethods = listOf(
        stringResource(R.string.payment_method_cb),
        stringResource(R.string.payment_method_cash),
        stringResource(R.string.payment_method_transfer),
        stringResource(R.string.payment_method_check)
    )
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: "CB") }
    var expandedPayment by remember { mutableStateOf(false) }

    // --- GESTION DU VIREMENT ---
    var targetAccountId by remember { mutableStateOf<String?>(null) }
    var expandedTargetAccount by remember { mutableStateOf(false) }
    var isTransferIncoming by remember { mutableStateOf(false) }

    val activity = LocalActivity.current
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

    var selectedDate by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.date?.toDate() ?: Date())
    }

    var isRecurring by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.isRecurring ?: false)
    }

    var endDate by remember {
        mutableStateOf<Date?>(initialTransaction?.endDate?.toDate())
    }

    var showExitConfirmation by remember { mutableStateOf(false) }
    val isDirty = title.isNotBlank() || (amountText.isNotBlank() && amountText != "0" && amountText != "0.0")

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
                    text = if (initialTransaction == null) stringResource(R.string.tx_new_title) else stringResource(R.string.tx_edit_title),
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
                        Text(stringResource(R.string.tx_type_expense))
                    }
                    SegmentedButton(
                        selected = type == "INCOME",
                        onClick = {
                            type = "INCOME"
                            familyCategory = context.getString(R.string.tx_income_family)
                            subCategory = incomeCategories.first()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text(stringResource(R.string.tx_type_income))
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
                        Text(stringResource(R.string.tx_type_transfer))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.tx_label_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { 
                        val isCrypto = accounts.find { it.id == currentAccountId }?.type == "CRYPTO"
                        Text(if (isCrypto) "Quantité (ex: 0.1)" else stringResource(R.string.tx_amount_hint)) 
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // --- CHAMP SPÉCIFIQUE CRYPTO (Montant en €) ---
            val currentAccount = accounts.find { it.id == currentAccountId }
            if (currentAccount?.type == "CRYPTO" && type != "EXPENSE") {
                item {
                    OutlinedTextField(
                        value = investmentEurText,
                        onValueChange = { investmentEurText = it },
                        label = { Text("Coût de l'achat (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Ex: 500.00") }
                    )
                }
            }

            if (type == "TRANSFER") {
                item {
                    val currentAccountName = accounts.find { it.id == currentAccountId }?.name ?: "Ce compte"
                    val targetAccountName = accounts.find { it.id == targetAccountId }?.name ?: "Autre compte"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        val targetAccount = accounts.find { it.id == targetAccountId }
                        OutlinedTextField(
                            value = targetAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.tx_target_account_label)) },
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
                            label = { Text(stringResource(R.string.tx_family_label)) },
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
                            label = { Text(stringResource(R.string.tx_subcategory_label)) },
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
                            label = { Text(stringResource(R.string.tx_income_category_label)) },
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
                        label = { Text(stringResource(R.string.tx_payment_method_label)) },
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
                        val cal = Calendar.getInstance().apply { time = selectedDate }
                        DatePickerDialog(
                            activity,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                                selectedDate = newCal.time
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.tx_date_prefix, dateFormat.format(selectedDate)))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.tx_recurring_label))
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
                            val baseDate = endDate ?: Date()
                            val cal = Calendar.getInstance().apply { time = baseDate }
                            DatePickerDialog(
                                activity,
                                { _, year, month, dayOfMonth ->
                                    val newCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                                    endDate = newCal.time
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val safeEndDate = endDate
                        val dateString = if (safeEndDate != null) dateFormat.format(safeEndDate) else ""
                        Text(text = if (safeEndDate != null) stringResource(R.string.tx_end_date_prefix, dateString) else stringResource(R.string.tx_end_date_none))
                    }
                }

                if (endDate != null) {
                    item {
                        TextButton(
                            onClick = { endDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.tx_clear_end_date))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val rawAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val finalAmount = if (type == "EXPENSE") -abs(rawAmount) else abs(rawAmount)
                        val finalFamily = when(type) {
                            "INCOME" -> context.getString(R.string.tx_income_family)
                            "TRANSFER" -> "Virement"
                            else -> familyCategory
                        }

                        if (title.isNotBlank() && rawAmount != 0.0) {
                            val safeEndDate = endDate
                            
                            // Déterminer source et destination selon le sens
                            val finalSourceId = if (type == "TRANSFER" && isTransferIncoming) targetAccountId ?: "" else currentAccountId
                            val finalTargetId = if (type == "TRANSFER" && isTransferIncoming) currentAccountId else targetAccountId

                            onSave(
                                title,
                                finalAmount,
                                finalFamily,
                                subCategory,
                                type,
                                paymentMethod,
                                Timestamp(selectedDate),
                                isRecurring,
                                if (isRecurring) "MONTHLY" else null,
                                if (isRecurring && safeEndDate != null) Timestamp(safeEndDate) else null,
                                finalSourceId,
                                finalTargetId,
                                investmentEurText.toDoubleOrNull()
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (initialTransaction == null) stringResource(R.string.tx_save_button) else stringResource(R.string.tx_edit_button))
                }
            }
        }
    }
}