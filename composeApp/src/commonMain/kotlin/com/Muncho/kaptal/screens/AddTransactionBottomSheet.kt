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
import com.muncho.kaptal.utils.*
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*
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

    val incomeCatSalary = stringResource(Res.string.income_cat_salary)
    val incomeCatHealth = stringResource(Res.string.income_cat_health)
    val incomeCatTransferInternal = stringResource(Res.string.income_cat_transfer_internal)
    val incomeCatTransferExternal = stringResource(Res.string.income_cat_transfer_external)
    val incomeCatTaxes = stringResource(Res.string.income_cat_taxes)
    val incomeCatOther = stringResource(Res.string.income_cat_other)

    val incomeCategories = listOf(
        incomeCatSalary, incomeCatHealth, incomeCatTransferInternal,
        incomeCatTransferExternal, incomeCatTaxes, incomeCatOther
    )

    val incomeFamilyStr = stringResource(Res.string.tx_income_family)
    val transferFamilyStr = stringResource(Res.string.tx_type_transfer)
    val transferInternalSubStr = stringResource(Res.string.tx_type_transfer_internal)

    var familyCategory by remember(incomeFamilyStr) {
        mutableStateOf(
            if (initialTransaction?.type == "INCOME") incomeFamilyStr
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
    var expandedRecurrence by remember { mutableStateOf(false) }

    val paymentMethodCb = stringResource(Res.string.payment_method_cb)
    val paymentMethodCash = stringResource(Res.string.payment_method_cash)
    val paymentMethodTransfer = stringResource(Res.string.payment_method_transfer)
    val paymentMethodCheck = stringResource(Res.string.payment_method_check)

    val paymentMethods = listOf(paymentMethodCb, paymentMethodCash, paymentMethodTransfer, paymentMethodCheck)
    var paymentMethod by remember(paymentMethods) { mutableStateOf(initialTransaction?.paymentMethod ?: paymentMethods.first()) }
    var expandedPayment by remember { mutableStateOf(false) }

    val recurrenceMonthly = stringResource(Res.string.tx_frequency_monthly)
    val recurrenceQuarterly = stringResource(Res.string.tx_frequency_quarterly)
    val recurrenceAnnual = stringResource(Res.string.tx_frequency_annual)
    val recurrenceCustom = stringResource(Res.string.tx_frequency_custom)

    val recurrenceLabels = remember(recurrenceMonthly, recurrenceQuarterly, recurrenceAnnual, recurrenceCustom) {
        mapOf(
            "MONTHLY" to recurrenceMonthly,
            "QUARTERLY" to recurrenceQuarterly,
            "ANNUAL" to recurrenceAnnual,
            "CUSTOM" to recurrenceCustom
        )
    }

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

    var recurrenceInterval by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.recurrenceInterval ?: "MONTHLY")
    }
    
    var customRecurrenceMonths by remember {
        mutableStateOf(
            if (recurrenceInterval.startsWith("CUSTOM_")) recurrenceInterval.substringAfter("CUSTOM_") else "1"
        )
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
            title = { Text(stringResource(Res.string.tx_abandon_title)) },
            text = { Text(stringResource(Res.string.tx_abandon_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onDismiss()
                }) {
                    Text(stringResource(Res.string.tx_abandon_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(Res.string.tx_abandon_cancel))
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
                    text = if (initialTransaction == null) stringResource(Res.string.tx_new_title) else stringResource(Res.string.tx_edit_title),
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
                        Text(stringResource(Res.string.tx_type_expense))
                    }
                    SegmentedButton(
                        selected = type == "INCOME",
                        onClick = {
                            type = "INCOME"
                            familyCategory = incomeFamilyStr
                            subCategory = incomeCategories.first()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text(stringResource(Res.string.tx_type_income))
                    }
                    SegmentedButton(
                        selected = type == "TRANSFER",
                        onClick = {
                            type = "TRANSFER"
                            familyCategory = transferFamilyStr
                            subCategory = transferInternalSubStr
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text(stringResource(Res.string.tx_type_transfer))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.tx_label_hint)) },
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
                        Text(if (isCurrentCrypto) stringResource(Res.string.tx_amount_crypto, cryptoSymbol) else stringResource(Res.string.tx_amount_eur)) 
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
                            label = { Text(stringResource(Res.string.tx_cost_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = feesPercentText,
                            onValueChange = { feesPercentText = it },
                            label = { Text(stringResource(Res.string.tx_fees_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            if (type == "TRANSFER") {
                item {
                    val currentAccountName = currentAccount?.name ?: stringResource(Res.string.tx_source_account_label)
                    val targetAccountName = targetAcc?.name ?: stringResource(Res.string.tx_target_account_label)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(Res.string.tx_direction_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                                        contentDescription = stringResource(Res.string.tx_direction_reverse),
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
                            label = { Text(stringResource(Res.string.tx_target_account_label)) },
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

            if (type == "EXPENSE" || type == "TRANSFER") {
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedFamily,
                        onExpandedChange = { expandedFamily = !expandedFamily }
                    ) {
                        OutlinedTextField(
                            value = familyCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.tx_family_label)) },
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
                            label = { Text(stringResource(Res.string.tx_subcategory_label)) },
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
                            label = { Text(stringResource(Res.string.tx_category_label)) },
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
                        label = { Text(stringResource(Res.string.tx_payment_method_label)) },
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
                    Text(text = stringResource(Res.string.tx_date_prefix, DateTimeUtils.formatDate(selectedDate, "dd/MM/yyyy")))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(Res.string.tx_recurrence_label))
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                }
            }

            if (isRecurring) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedRecurrence,
                        onExpandedChange = { expandedRecurrence = !expandedRecurrence }
                    ) {
                        OutlinedTextField(
                            value = recurrenceLabels[if (recurrenceInterval.startsWith("CUSTOM_")) "CUSTOM" else recurrenceInterval] ?: recurrenceLabels["MONTHLY"]!!,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.tx_frequency_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedRecurrence,
                            onDismissRequest = { expandedRecurrence = false }
                        ) {
                            recurrenceLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        recurrenceInterval = if (key == "CUSTOM") "CUSTOM_$customRecurrenceMonths" else key
                                        expandedRecurrence = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (recurrenceInterval.startsWith("CUSTOM_")) {
                    item {
                        OutlinedTextField(
                            value = customRecurrenceMonths,
                            onValueChange = { 
                                val filtered = it.filter { char -> char.isDigit() }
                                if (filtered.isNotEmpty()) {
                                    customRecurrenceMonths = filtered
                                    recurrenceInterval = "CUSTOM_$filtered"
                                } else {
                                    customRecurrenceMonths = ""
                                }
                            },
                            label = { Text(stringResource(Res.string.tx_months_count_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

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
                        Text(text = if (safeEndDate != null) stringResource(Res.string.tx_end_date_prefix, dateString) else stringResource(Res.string.tx_end_date_none))
                    }
                }

                if (endDate != null) {
                    item {
                        TextButton(
                            onClick = { endDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.tx_delete_end_date))
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
                            "INCOME" -> incomeFamilyStr
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
                                DateTimeUtils.toSafeInstant(selectedDate).toTimestamp(),
                                isRecurring,
                                if (isRecurring) recurrenceInterval else null,
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
                    Text(if (initialTransaction == null) stringResource(Res.string.tx_save_button) else stringResource(Res.string.tx_edit_button))
                }
            }
        }
    }
}


