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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, familyCategory: String, subCategory: String, type: String, paymentMethod: String, date: Timestamp, isRecurring: Boolean, recurrenceInterval: String?, endDate: Timestamp?) -> Unit
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

    val paymentMethods = listOf("CB", "Espèces", "Virement", "Chèque")
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: "CB") }
    var expandedPayment by remember { mutableStateOf(false) }

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
                            familyCategory = transactionCategories.first().name
                            subCategory = transactionCategories.first().subCategories.first()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
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
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Recette")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Libellé (ex: Courses, Salaire...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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
                            label = { Text("Grande famille de dépense") },
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
                            label = { Text("Catégorie de recette") },
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
                        label = { Text("Source des fonds / Paiement") },
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
                    Text(text = "Date : ${dateFormat.format(selectedDate)}")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Opération récurrente (mensuelle)")
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
                        Text(text = if (safeEndDate != null) "Date de fin : $dateString" else "Pas de date de fin (Infini)")
                    }
                }

                if (endDate != null) {
                    item {
                        TextButton(
                            onClick = { endDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Effacer la date de fin (Infini)")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val rawAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val finalAmount = if (type == "EXPENSE") -abs(rawAmount) else abs(rawAmount)
                        val finalFamily = if (type == "INCOME") "Recettes" else familyCategory

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
                                if (isRecurring && safeEndDate != null) Timestamp(safeEndDate) else null
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