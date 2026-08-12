package com.example.kaptal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptal.R
import com.example.kaptal.model.Account
import com.example.kaptal.viewmodel.AccountDetailViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountScreen(
    account: Account,
    allAccounts: List<Account> = emptyList(),
    onBackClick: () -> Unit,
    viewModel: AccountDetailViewModel = viewModel()
) {
    val linkedAccount = allAccounts.find { it.id == account.linkedAccountId }
    var showSetupDialog by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
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
                // Info sur le compte lié
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = stringResource(R.string.credit_setup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (linkedAccount != null) {
                                    Text(
                                        text = stringResource(R.string.credit_linked_account_label, linkedAccount.name),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showSetupDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Configurer")
                        }
                    }
                }

                // Bouton Import PDF (Placeholder pour le moment)
                Button(
                    onClick = { /* TODO: Implémenter le picker de PDF et le parser */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.credit_pdf_import))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Suivi des mensualités en cours de construction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSetupDialog) {
        var installmentAmount by remember { mutableStateOf("") }
        var durationMonths by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text(stringResource(R.string.credit_setup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = installmentAmount,
                        onValueChange = { installmentAmount = it },
                        label = { Text(stringResource(R.string.credit_installment_amount)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = durationMonths,
                        onValueChange = { durationMonths = it },
                        label = { Text(stringResource(R.string.credit_duration_months)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = installmentAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val months = durationMonths.toIntOrNull() ?: 0
                    if (amount > 0 && months > 0) {
                        viewModel.generateInstallments(
                            accountId = account.id,
                            linkedAccountId = account.linkedAccountId,
                            amount = amount,
                            months = months,
                            startDate = Date(),
                            title = "Prélèvement ${account.name}"
                        )
                        showSetupDialog = false
                    }
                }) {
                    Text(stringResource(R.string.credit_generate_button))
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
