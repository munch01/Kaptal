package com.example.kaptal.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kaptal.R
import com.example.kaptal.model.Account
import com.example.kaptal.model.Transaction
import com.example.kaptal.model.transactionCategories
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
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, familyCategory: String, subCategory: String, type: String, paymentMethod: String, date: Timestamp, isRecurring: Boolean, recurrenceInterval: String?, endDate: Timestamp?, targetAccountId: String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }

    val initialAbsAmount = initialTransaction?.amount?.let { if (it < 0) -it else it }
    var amountText by remember { mutableStateOf(initialAbsAmount?.let { if (it == 0.0) "" else it.toString() } ?: "") }

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
            else initialTransaction?.familyCategory?.takeIf { it.isNotBlank() } ?: transactionCategories.first().name
        )
    }

    var subCategory by remember {
        mutableStateOf(
            if (initialTransaction?.type == "INCOME") initialTransaction.subCategory?.takeIf { it.isNotBlank() } ?: incomeCategories.first()
            else initialTransaction?.subCategory?.takeIf { it.isNotBlank() } ?: transactionCategories.first().subCategories.first()
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

    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

    var selectedDate by remember {
        mutableStateOf(initialTransaction?.date?.toDate() ?: Date())
    }

    var isRecurring by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.isRecurring ?: false)
    }

    var endDate by remember {
        mutableStateOf<Date?>(initialTransaction?.endDate?.toDate())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                            familyCategory = transactionCategories.first().name
                            subCategory = transactionCategories.first().subCategories.first()
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
                    label = { Text(stringResource(R.string.tx_amount_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (type == "TRANSFER") {
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
                            transactionCategories.forEach { family ->
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
                    val currentFamilyObj = transactionCategories.find { it.name == familyCategory }
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
                            context,
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
                                context,
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
                                targetAccountId
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