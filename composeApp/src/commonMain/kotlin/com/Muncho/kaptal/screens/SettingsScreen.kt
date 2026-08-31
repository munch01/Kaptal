package com.muncho.kaptal.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.getPlatform
import com.muncho.kaptal.model.Account
import com.muncho.kaptal.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToCategories: () -> Unit,
    allAccounts: List<Account> = emptyList(),
    onExportClick: (Account?, String) -> Unit,
    viewModel: SettingsViewModel
) {
    val platform = getPlatform()
    val currentUser = viewModel.currentUser
    val appVersion = "2.0.0-portage"

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("CSV") }

    val availableCurrencies = listOf("EUR (€)", "USD ($)", "GBP (£)")
    val availableLanguages = listOf("Français", "English", "Español")

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_k_logo),
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
                            stringResource(Res.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.settings_profile_section),
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
                                    text = currentUser?.email ?: stringResource(Res.string.settings_user_default),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser?.email ?: stringResource(Res.string.settings_email_not_set),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_security_section),
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
                                    Text(stringResource(Res.string.settings_biometric_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(Res.string.settings_biometric_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = viewModel.isBiometricEnabled,
                                onCheckedChange = { viewModel.updateBiometric(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.Lock,
                            title = stringResource(Res.string.settings_password_title),
                            subtitle = stringResource(Res.string.settings_password_subtitle),
                            onClick = {
                                viewModel.sendPasswordResetEmail { success, msg ->
                                    platform.showToast(msg)
                                }
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_preferences_section),
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
                            title = stringResource(Res.string.settings_currency_title),
                            subtitle = viewModel.selectedCurrency,
                            onClick = { showCurrencyDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.Language,
                            title = stringResource(Res.string.settings_language_title),
                            subtitle = viewModel.selectedLanguage,
                            onClick = { showLanguageDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.Category,
                            title = stringResource(Res.string.settings_manage_categories),
                            subtitle = stringResource(Res.string.settings_manage_categories_desc),
                            onClick = onNavigateToCategories
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_about_section),
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
                            title = stringResource(Res.string.settings_github_title),
                            subtitle = stringResource(Res.string.settings_github_subtitle),
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = { platform.openUrl("https://github.com/munch01/Kaptal") }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.Gavel,
                            title = stringResource(Res.string.settings_open_source_title),
                            subtitle = stringResource(Res.string.settings_open_source_subtitle),
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = { platform.openUrl("https://github.com/munch01/Kaptal/blob/master/LICENSE") }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(Res.string.settings_privacy_title),
                            subtitle = stringResource(Res.string.settings_privacy_subtitle),
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = { platform.openUrl("https://munch01.github.io/Kaptal/index.md") }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.Info,
                            title = stringResource(Res.string.settings_about_title),
                            subtitle = stringResource(Res.string.settings_version, appVersion),
                            onClick = { showAboutDialog = true }
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.section_backup),
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
                            title = stringResource(Res.string.export_csv),
                            subtitle = stringResource(Res.string.export_csv) + " CSV",
                            onClick = { 
                                exportFormat = "CSV"
                                showExportDialog = true 
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsClickableItem(
                            icon = Icons.Default.DataObject,
                            title = stringResource(Res.string.export_json),
                            subtitle = stringResource(Res.string.export_json) + " (JSON)",
                            onClick = { 
                                exportFormat = "JSON"
                                showExportDialog = true 
                            }
                        )
                    }
                }

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
                    Text(stringResource(Res.string.settings_logout))
                }

                Button(
                    onClick = { showDeleteAccountDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_delete_account))
                }
            }
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(Res.string.settings_currency_dialog)) },
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
                            RadioButton(selected = (curr == viewModel.selectedCurrency), onClick = {
                                viewModel.updateCurrency(curr)
                                showCurrencyDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = curr, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text(stringResource(Res.string.cancel_label)) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(Res.string.settings_language_dialog)) },
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
                            RadioButton(selected = (lang == viewModel.selectedLanguage), onClick = {
                                viewModel.updateLanguage(lang)
                                showLanguageDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lang, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(Res.string.cancel_label)) }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_about_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.settings_about_description))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(Res.string.settings_version, appVersion), fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.settings_about_tech))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(Res.string.close_label)) }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(Res.string.settings_export_dialog_title, exportFormat)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (allAccounts.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onExportClick(null, exportFormat)
                                    showExportDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AllInclusive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stringResource(Res.string.settings_export_all), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = stringResource(Res.string.settings_export_all_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    }

                    allAccounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onExportClick(account, exportFormat)
                                    showExportDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = account.bankName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (allAccounts.isEmpty()) {
                        Text(stringResource(Res.string.settings_export_no_accounts), modifier = Modifier.padding(16.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(Res.string.cancel_label)) }
            }
        )
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
