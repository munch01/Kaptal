package com.Muncho.kaptal.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.MainViewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.findActivity
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
    detailViewModel: AccountDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val linkedAccount = allAccounts.find { it.id == account.linkedAccountId }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showChartSheet by remember { mutableStateOf(false) }
    
    val importStatus by detailViewModel.importStatus.collectAsState()
    val transactions by detailViewModel.transactions.collectAsState()
    val pdfRows by detailViewModel.pdfRows.collectAsState()

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { detailViewModel.extractPdfData(context, it) }
    }

    LaunchedEffect(account.id) {
        detailViewModel.loadTransactions(account.id)
    }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
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
        // 1. Fond général
        Image(
            painter = painterResource(id = R.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        // 2. Logo central en filigrane
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_k_logo),
                contentDescription = "Logo K Kaptal",
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.Fit,
                alpha = 0.15f
            )
        }

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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_label)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showChartSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = stringResource(R.string.account_detail_chart),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. FICHE DE PRÊT (CENTRALE)
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
                                Text(
                                    text = "Fiche de Prêt",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row {
                                // Bouton PDF (Importation)
                                IconButton(onClick = { pdfLauncher.launch("application/pdf") }) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Importer PDF", tint = MaterialTheme.colorScheme.secondary)
                                }
                                // Bouton + (Saisie Manuelle)
                                IconButton(onClick = { showSetupDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Saisie manuelle", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (linkedAccount != null) {
                            Text(
                                text = stringResource(R.string.credit_linked_account_label, linkedAccount.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                            // --- AFFICHAGE DES INFOS (Si configuré) ---
                            if (!account.loanStartDate.isNullOrBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                // Calculs dynamiques
                                val now = Calendar.getInstance().timeInMillis
                                val pastTxs = transactions.filter { it.type == "INCOME" && it.date.toDate().time <= now }
                                val futureTxs = transactions.filter { it.type == "INCOME" && it.date.toDate().time > now }
                                
                                val amountAlreadyPaid = pastTxs.sumOf { it.amount }
                                val amountRemaining = futureTxs.sumOf { it.amount }
                                val totalCost = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("CAPITAL EMPRUNTÉ", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("%.2f €".format(account.totalAmount ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                                        Text("%.2f € / mois".format(monthly), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Déjà payé : %.2f €".format(amountAlreadyPaid), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("TOTAL À REMBOURSER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text("%.2f €".format(totalCost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Restant : %.2f €".format(amountRemaining), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            } else {
                            // Message si non configuré
                            Text(
                                "Crédit non configuré. Utilisez les icônes ci-dessus pour importer un PDF ou saisir les données manuellement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Liste des mensualités (Échéancier)
                val sortedTxs = remember(transactions) { transactions.sortedBy { it.date } }

                if (sortedTxs.isEmpty()) {
                    Text(
                        text = "Aucune mensualité importée. Utilisez les boutons ci-dessus pour configurer votre crédit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Échéancier (${sortedTxs.size} mois)",
                        style = MaterialTheme.typography.titleSmall,
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
                                            text = "Mois n°${index + 1} - ${SimpleDateFormat("MMM yyyy", Locale.FRANCE).format(tx.date.toDate())}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "%.2f €".format(tx.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Détail de la répartition
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Principal", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("%.2f €".format(tx.principalPart ?: 0.0), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Intérêts", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("%.2f €".format(tx.interestPart ?: 0.0), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Assurance", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("%.2f €".format(tx.insurancePart ?: 0.0), style = MaterialTheme.typography.bodySmall, color = Color(0xFF1976D2))
                                        }
                                        if (tx.remainingDebt != null) {
                                            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                                Text("Restant dû", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text("%.2f €".format(tx.remainingDebt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
            // On prend le mois en cours par défaut pour le crédit
            val now = Calendar.getInstance()
            CategoryDistributionDialog(
                transactions = transactions,
                year = now.get(Calendar.YEAR),
                month = now.get(Calendar.MONTH),
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
        // Pré-remplissage
        var firstInstallmentDate by remember { 
            mutableStateOf(
                try {
                    if (account.loanStartDate != null) 
                        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse(account.loanStartDate) ?: Date()
                    else Date()
                } catch (e: Exception) { Date() }
            ) 
        }
        var monthlyPayment by remember { mutableStateOf(account.loanMonthlyPayment?.toString() ?: "") }
        var durationMonths by remember { mutableStateOf(if (transactions.isNotEmpty()) transactions.size.toString() else "") }
        var insurance by remember { mutableStateOf(account.loanInsurance?.toString() ?: "") }
        var rate by remember { mutableStateOf(account.loanRate?.toString() ?: "") }
        var totalCapital by remember { mutableStateOf(account.totalAmount?.toString() ?: "") }
        var withdrawalDay by remember { mutableStateOf("5") }
        
        var editMode by remember { mutableStateOf(if (transactions.isNotEmpty()) 0 else 1) } // 0: Capital seul, 1: Tout
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

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
                                    // On vide les champs pour éviter les conflits
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Mise à jour rapide",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Ajustez votre capital emprunté. Kaptal nettoiera automatiquement vos libellés existants.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = totalCapital, 
                            onValueChange = { totalCapital = it }, 
                            label = { Text("Capital Emprunté Réel (€)") }, 
                            placeholder = { Text("Ex: 13300") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                val activity = context.findActivity() ?: return@OutlinedButton
                                val cal = Calendar.getInstance().apply { time = firstInstallmentDate }
                                DatePickerDialog(activity, { _, year, month, day ->
                                    firstInstallmentDate = Calendar.getInstance().apply { set(year, month, day) }.time
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1ère échéance : ${dateFormat.format(firstInstallmentDate)}")
                        }

                        OutlinedTextField(
                            value = totalCapital, 
                            onValueChange = { totalCapital = it }, 
                            label = { Text("Capital Emprunté Réel (€)") }, 
                            placeholder = { Text("Ex: 13300") },
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
                                context = context,
                                account = account,
                                startDate = firstInstallmentDate,
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
                    Text(stringResource(R.string.cancel_label))
                }
            }
        )
    }
}
