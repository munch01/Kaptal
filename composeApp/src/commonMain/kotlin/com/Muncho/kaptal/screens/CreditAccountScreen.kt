package com.muncho.kaptal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.utils.*
import com.muncho.kaptal.viewmodel.AccountDetailViewModel
import com.muncho.kaptal.viewmodel.MainViewModel
import org.jetbrains.compose.resources.painterResource
import kaptal.composeapp.generated.resources.*
import kotlinx.datetime.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    userCategories: List<com.muncho.kaptal.model.CategoryFamily> = emptyList(),
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel,
    detailViewModel: AccountDetailViewModel
) {
    val platform = getPlatform()
    val linkedAccount = allAccounts.find { it.id == account.linkedAccountId }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showChartSheet by remember { mutableStateOf(false) }
    
    val importStatus by detailViewModel.importStatus.collectAsState()
    val transactions by detailViewModel.transactions.collectAsState()
    val pdfRows by detailViewModel.pdfRows.collectAsState()

    LaunchedEffect(account.id) {
        detailViewModel.loadTransactions(account.id)
    }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            platform.showToast(it)
            if (it.startsWith("Échéancier généré") || it.startsWith("Importation réussie") || it.startsWith("Erreur")) {
                detailViewModel.clearImportStatus()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        Image(
            painter = painterResource(Res.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showChartSheet = true }) {
                            Icon(imageVector = Icons.Default.PieChart, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(text = "Fiche de Prêt", fontWeight = FontWeight.Bold)
                            }
                            
                            Row {
                                IconButton(onClick = { 
                                    platform.pickFile("application/pdf") { uri ->
                                        uri?.let { detailViewModel.extractPdfData(it) }
                                    }
                                }) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }
                                IconButton(onClick = { showSetupDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (linkedAccount != null) {
                            Text(
                                text = "Lié à : ${linkedAccount.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!account.loanStartDate.isNullOrBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            val now = DateTimeUtils.now()
                            val pastTxs = transactions.filter { it.type == "INCOME" && it.date.toInstant() <= now }
                            val futureTxs = transactions.filter { it.type == "INCOME" && it.date.toInstant() > now }
                            
                            val amountAlreadyPaid = pastTxs.sumOf { it.amount }
                            val amountRemaining = futureTxs.sumOf { it.amount }
                            val totalCost = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CAPITAL EMPRUNTÉ", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("${(account.totalAmount ?: 0.0).roundTo(2)} €", fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("ÉCHÉANCE FINALE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("${account.loanEndDate ?: "Inconnue"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("MENSUALITÉ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    val monthly = (account.loanMonthlyPayment ?: 0.0) + (account.loanInsurance ?: 0.0)
                                    Text("${monthly.roundTo(2)} € / mois", fontWeight = FontWeight.Bold)
                                    Text("Déjà payé : ${amountAlreadyPaid.roundTo(2)} €", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("TOTAL À REMBOURSER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("${totalCost.roundTo(2)} €", fontWeight = FontWeight.Bold)
                                    Text("Restant : ${amountRemaining.roundTo(2)} €", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        } else {
                            Text(
                                "Crédit non configuré.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val sortedTxs = remember(transactions) { transactions.sortedBy { it.date.toInstant() } }

                if (sortedTxs.isNotEmpty()) {
                    Text(
                        text = "Échéancier (${sortedTxs.size} mois)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedTxs.forEachIndexed { index, tx ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Mois n°${index + 1} - ${DateTimeUtils.formatDate(tx.date.toInstant(), "MMM yyyy")}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${tx.amount.roundTo(2)} €",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Principal", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${(tx.principalPart ?: 0.0).roundTo(2)} €", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Intérêts", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${(tx.interestPart ?: 0.0).roundTo(2)} €", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Assurance", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${(tx.insurancePart ?: 0.0).roundTo(2)} €", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1976D2))
                                        }
                                        if (tx.remainingDebt != null) {
                                            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                                Text("Restant dû", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text("${tx.remainingDebt.roundTo(2)} €", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showChartSheet) {
            val now = DateTimeUtils.now()
            CategoryDistributionDialog(
                transactions = transactions,
                year = DateTimeUtils.getYear(now),
                month = DateTimeUtils.getMonth(now),
                onDismiss = { showChartSheet = false }
            )
        }
    }

    if (pdfRows.isNotEmpty()) {
        PdfColumnPickerSheet(
            rows = pdfRows,
            onDismiss = { detailViewModel.clearPdfData() },
            onConfirm = { startRowIdx, dateColIdx, amountColIdx, principalColIdx, interestColIdx, insuranceColIdx, remainingDebtColIdx ->
                detailViewModel.importFromSelectedColumns(
                    accountId = account.id,
                    linkedAccountId = account.linkedAccountId,
                    startRowIdx = startRowIdx,
                    dateColIdx = dateColIdx,
                    amountColIdx = amountColIdx,
                    principalColIdx = principalColIdx,
                    interestColIdx = interestColIdx,
                    insuranceColIdx = insuranceColIdx,
                    remainingDebtColIdx = remainingDebtColIdx
                )
            }
        )
    }

    if (showSetupDialog) {
        var firstInstallmentInstant by remember { 
            mutableStateOf(DateTimeUtils.now()) 
        }
        var monthlyPayment by remember { mutableStateOf(account.loanMonthlyPayment?.toString() ?: "") }
        var durationMonths by remember { mutableStateOf(if (transactions.isNotEmpty()) transactions.size.toString() else "") }
        var insurance by remember { mutableStateOf(account.loanInsurance?.toString() ?: "") }
        var rate by remember { mutableStateOf(account.loanRate?.toString() ?: "") }
        var totalCapital by remember { mutableStateOf(account.totalAmount?.toString() ?: "") }
        var withdrawalDay by remember { mutableStateOf("5") }
        
        var editMode by remember { mutableStateOf(if (transactions.isNotEmpty()) 0 else 1) }

        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text(if (editMode == 0) "Ajuster le Capital" else "Configuration du prêt") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (transactions.isNotEmpty()) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = editMode == 0,
                                onClick = { 
                                    editMode = 0
                                    monthlyPayment = ""
                                    durationMonths = ""
                                    rate = ""
                                },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("Capital seul", fontSize = 10.sp) }
                            SegmentedButton(
                                selected = editMode == 1,
                                onClick = { editMode = 1 },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("Régénérer tout", fontSize = 10.sp) }
                        }
                    }

                    if (editMode == 0) {
                        OutlinedTextField(
                            value = totalCapital, 
                            onValueChange = { totalCapital = it }, 
                            label = { Text("Capital Emprunté Réel (€)") }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                platform.pickDate(firstInstallmentInstant.toEpochMilliseconds()) {
                                    firstInstallmentInstant = Instant.fromEpochMilliseconds(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1ère échéance : ${DateTimeUtils.formatDate(firstInstallmentInstant, "dd/MM/yyyy")}")
                        }

                        OutlinedTextField(
                            value = totalCapital, 
                            onValueChange = { totalCapital = it }, 
                            label = { Text("Capital Emprunté Réel (€)") }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = monthlyPayment, onValueChange = { monthlyPayment = it }, label = { Text("Mensualité (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = insurance, onValueChange = { insurance = it }, label = { Text("Assurance (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = durationMonths, onValueChange = { durationMonths = it }, label = { Text("Durée (mois)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = withdrawalDay, onValueChange = { withdrawalDay = it }, label = { Text("Jour prélèvement") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        
                        OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Taux (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                val cVal = totalCapital.replace(",", ".").toDoubleOrNull() ?: 0.0
                Button(onClick = {
                    if (editMode == 0) {
                        if (cVal > 0) {
                            detailViewModel.updateLoanMetadata(account.id, cVal, account.name)
                            showSetupDialog = false
                        }
                    } else {
                        val m = monthlyPayment.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val d = durationMonths.toIntOrNull() ?: 0
                        val i = insurance.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val r = rate.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val wDay = withdrawalDay.toIntOrNull() ?: 5
                        
                        if (m > 0 && d > 0 && cVal > 0) {
                            detailViewModel.generateLoanInstallments(
                                account = account,
                                startDateInstant = firstInstallmentInstant,
                                monthlyPayment = m,
                                durationMonths = d,
                                totalCapital = cVal,
                                insurance = i,
                                rate = r,
                                withdrawalDay = wDay
                            )
                            showSetupDialog = false
                        }
                    }
                }) {
                    Text(if (editMode == 0) "Mettre à jour" else "Générer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

private fun dev.gitlive.firebase.firestore.Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(this.seconds, this.nanoseconds)
