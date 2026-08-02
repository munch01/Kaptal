package com.example.kaptal.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
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
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    initialTransaction: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: String, type: String, paymentMethod: String, date: Timestamp, isRecurring: Boolean, recurrenceInterval: String?, endDate: Timestamp?) -> Unit
) {
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }

    val initialAbsAmount = initialTransaction?.amount?.let { if (it < 0) -it else it }
    var amountText by remember { mutableStateOf(initialAbsAmount?.let { if (it == 0.0) "" else it.toString() } ?: "") }

    var type by remember { mutableStateOf(initialTransaction?.type ?: "EXPENSE") }

    val expenseCategories = listOf(
        "Nourriture", "Loisir", "Sport", "Essence", "Energie",
        "Téléphonie et internet", "Assurance", "Crédit ou loyer",
        "Crédit conso", "Impôt", "Abonnement", "Ecole",
        "Frais pro", "Médecine", "Beauté et bien-être",
        "Virement entre compte", "Divers"
    )

    val incomeCategories = listOf(
        "Salaire", "Remboursement santé", "Virement entre compte",
        "Virement divers", "Impôts", "Autre / Divers"
    )

    var category by remember {
        mutableStateOf(
            initialTransaction?.category ?: expenseCategories.first()
        )
    }
    var expandedCategory by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (initialTransaction == null) "Nouvelle opération" else "Modifier l'opération",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == "EXPENSE",
                    onClick = {
                        type = "EXPENSE"
                        if (!expenseCategories.contains(category)) {
                            category = expenseCategories.first()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Dépense")
                }
                SegmentedButton(
                    selected = type == "INCOME",
                    onClick = {
                        type = "INCOME"
                        if (!incomeCategories.contains(category)) {
                            category = incomeCategories.first()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Recette")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Libellé (ex: Courses, Salaire...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Montant (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Catégorie") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    val currentList = if (type == "EXPENSE") expenseCategories else incomeCategories
                    currentList.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

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

            if (isRecurring) {
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
                    Text(text = if (endDate != null) "Date de fin : ${dateFormat.format(endDate!!)}" else "Pas de date de fin (Infini)")
                }

                if (endDate != null) {
                    TextButton(
                        onClick = { endDate = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Effacer la date de fin (Infini)")
                    }
                }
            }

            Button(
                onClick = {
                    val rawAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val finalAmount = if (type == "EXPENSE") -abs(rawAmount) else abs(rawAmount)

                    if (title.isNotBlank() && rawAmount != 0.0) {
                        onSave(
                            title,
                            finalAmount,
                            category,
                            type,
                            paymentMethod,
                            Timestamp(selectedDate),
                            isRecurring,
                            if (isRecurring) "MONTHLY" else null,
                            if (isRecurring && endDate != null) Timestamp(endDate!!) else null
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (initialTransaction == null) "Enregistrer" else "Modifier")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun abs(value: Double): Double = if (value < 0) -value else value