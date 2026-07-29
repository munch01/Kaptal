package com.example.kaptal

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                text = "Profil & Compte",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
                                text = currentUser?.displayName ?: "Utilisateur Kaptal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUser?.email ?: "Email non renseigné",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Email,
                        title = "Adresse e-mail",
                        subtitle = currentUser?.email ?: "Modifier votre adresse e-mail",
                        onClick = { showEmailDialog = true }
                    )
                }
            }

            HorizontalDivider()

            // ================= 2. SÉCURITÉ =================
            Text(
                text = "Sécurité",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
                                Text("Verrouillage biométrique", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Demander l'empreinte à l'ouverture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        title = "Mot de passe",
                        subtitle = "Envoyer un e-mail de réinitialisation",
                        onClick = {
                            viewModel.sendPasswordResetEmail { success, message ->
                                Toast.makeText(context, message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            // ================= 3. PRÉFÉRENCES =================
            Text(
                text = "Préférences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.CurrencyExchange,
                        title = "Devise principale",
                        subtitle = viewModel.selectedCurrency,
                        onClick = { showCurrencyDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Language,
                        title = "Langue de l'application",
                        subtitle = viewModel.selectedLanguage,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            HorizontalDivider()

            // ================= 4. DÉVELOPPEMENT & PROJET =================
            Text(
                text = "Développement",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.Code,
                        title = "Projet GitHub",
                        subtitle = "Consulter le code source",
                        trailingIcon = Icons.Default.OpenInNew,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsClickableItem(
                        icon = Icons.Default.Info,
                        title = "À propos de Kaptal",
                        subtitle = "Version 1.0.0",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================= 5. DÉCONNEXION & SUPPRESSION =================
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onBackClick()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Se déconnecter")
            }

            Button(
                onClick = { showDeleteAccountDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Supprimer mon compte")
            }
        }
    }

    // --- DIALOGUES ---
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Choisir la devise") },
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
            dismissButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text("Annuler") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Choisir la langue") },
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
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Annuler") } }
        )
    }

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Changer d'adresse e-mail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saisissez votre nouvelle adresse email :")
                    OutlinedTextField(
                        value = newEmailText,
                        onValueChange = { newEmailText = it },
                        label = { Text("Nouvel Email") },
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
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            showEmailDialog = false
                        }
                    }
                ) { Text("Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { showEmailDialog = false }) { Text("Annuler") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Kaptal") },
            text = {
                Column {
                    Text("Application de gestion financière personnelle.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version : 1.0.0", fontWeight = FontWeight.Bold)
                    Text("Architecture MVVM avec Jetpack Compose & Firebase.")
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Fermer") } }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Supprimer définitivement le compte ?") },
            text = { Text("Cette action est irréversible. Toutes vos données seront effacées.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteAccount { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            showDeleteAccountDialog = false
                            if (success) onBackClick()
                        }
                    }
                ) { Text("Confirmer la suppression") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Annuler") } }
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