package com.Muncho.kaptal

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Muncho.kaptal.R
import com.Muncho.kaptal.model.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    allAccounts: List<Account> = emptyList(),
    allBalances: Map<String, Double> = emptyMap(),
    viewModel: SettingsViewModel = viewModel()
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val currentUser = viewModel.currentUser

    // Dialogues
    var showEmailDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var newEmailText by remember { mutableStateOf("") }

    val availableCurrencies = listOf("EUR (€)", "USD ($)", "GBP (£)")
    val availableLanguages = listOf("Français", "English", "Español")

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        try {
            uri?.let {
                val data = viewModel.getExportDataJson(allAccounts, allBalances)
                activity.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(data.toByteArray())
                }
                Toast.makeText(activity, "Kaptal_Data.json enregistré !", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Erreur lors de l'export JSON", Toast.LENGTH_SHORT).show()
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        try {
            uri?.let {
                val data = viewModel.getExportDataCsv(allAccounts, allBalances)
                activity.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(data.toByteArray())
                }
                Toast.makeText(activity, "Kaptal_Export.csv enregistré !", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Erreur lors de l'export CSV", Toast.LENGTH_SHORT).show()
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
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
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

            // ================= 1. PROFIL ET COMPTE =================
            Text(
                text = stringResource(R.string.settings_profile_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f))
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: stringResource(R.string.settings_user_default),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUser?.email ?: stringResource(R.string.settings_email_not_set),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Email,
                        title = stringResource(R.string.settings_email_title),
                        subtitle = currentUser?.email ?: stringResource(R.string.settings_email_subtitle),
                        onClick = { showEmailDialog = true }
                    )
                }
            }

            HorizontalDivider()

            // ================= 2. SÉCURITÉ =================
            Text(
                text = stringResource(R.string.settings_security_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f))
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(stringResource(R.string.settings_biometric_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.settings_biometric_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Switch(
                            checked = viewModel.isBiometricEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateBiometric(checked)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.settings_password_title),
                        subtitle = stringResource(R.string.settings_password_subtitle),
                        onClick = {
                            viewModel.sendPasswordResetEmail { success, message ->
                                Toast.makeText(activity, message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            // ================= 3. PRÉFÉRENCES =================
            Text(
                text = stringResource(R.string.settings_preferences_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f))
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.CurrencyExchange,
                        title = stringResource(R.string.settings_currency_title),
                        subtitle = viewModel.selectedCurrency,
                        onClick = { showCurrencyDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language_title),
                        subtitle = viewModel.selectedLanguage,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            HorizontalDivider()

            // ================= 4. DÉVELOPPEMENT & LÉGAL =================
            Text(
                text = stringResource(R.string.settings_about_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f))
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.settings_github_title),
                        subtitle = stringResource(R.string.settings_github_subtitle),
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/munch01/Kaptal")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Gavel,
                        title = stringResource(R.string.settings_open_source_title),
                        subtitle = stringResource(R.string.settings_open_source_subtitle),
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/munch01/Kaptal/blob/master/LICENSE")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.PrivacyTip,
                        title = stringResource(R.string.settings_privacy_title),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://munch01.github.io/Kaptal/index.md")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_about_title),
                        subtitle = stringResource(R.string.settings_version, "1.0.120"),
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            HorizontalDivider()

            // ================= 5. DONNÉES & SAUVEGARDE (RGPD) =================
            Text(
                text = stringResource(R.string.section_backup),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f))
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.export_csv),
                        subtitle = "Exporter mes transactions au format Excel",
                        onClick = {
                            csvLauncher.launch("Kaptal_Export_${System.currentTimeMillis()}.csv")
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.DataObject,
                        title = stringResource(R.string.export_json),
                        subtitle = "Exporter mes données personnelles (Portabilité)",
                        onClick = {
                            jsonLauncher.launch("Kaptal_Data_${System.currentTimeMillis()}.json")
                        }
                    )
                }
            }

            // ================= 6. DÉCONNEXION & SUPPRESSION =================
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onBackClick()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_logout))
            }

            Button(
                onClick = { showDeleteAccountDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_delete_account))
            }
        }
    }

    // --- DIALOGUES ---
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_currency_dialog)) },
            text = {
                Column {
                    availableCurrencies.forEach { curr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateCurrency(curr)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (curr == viewModel.selectedCurrency),
                                onClick = {
                                    viewModel.updateCurrency(curr)
                                    showCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = curr, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language_dialog)) },
            text = {
                Column {
                    availableLanguages.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (lang == viewModel.selectedLanguage),
                                onClick = {
                                    viewModel.updateLanguage(lang)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lang, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text(stringResource(R.string.settings_email_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_email_dialog_text))
                    OutlinedTextField(
                        value = newEmailText,
                        onValueChange = { newEmailText = it },
                        label = { Text(stringResource(R.string.settings_email_new_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newEmailText.isNotBlank(),
                    onClick = {
                        viewModel.updateEmail(newEmailText) { _, msg ->
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                            showEmailDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.settings_email_update_button)) }
            },
            dismissButton = { TextButton(onClick = { showEmailDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_about_description))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_version, "1.0.120"), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_about_tech))
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.close_label)) } }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_text)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteAccount { success, msg ->
                            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                            showDeleteAccountDialog = false
                            if (success) onBackClick()
                        }
                    }
                ) { Text(stringResource(R.string.settings_delete_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
}
}


@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
