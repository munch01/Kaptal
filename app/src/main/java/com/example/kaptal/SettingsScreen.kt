package com.example.kaptal // Vérifie que ce nom de package correspond au tien

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    auth: FirebaseAuth,
    accounts: List<Account>,
    onBackClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // États Préférences
    var selectedCurrency by remember { mutableStateOf("EUR (€)") }
    var selectedLanguage by remember { mutableStateOf("Français") }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    // États Sécurité
    var isBiometricEnabled by remember { mutableStateOf(false) }
    var isPinEnabled by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var userPin by remember { mutableStateOf("") }

    // États Modales Compte
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val currencies = listOf("EUR (€)", "USD ($)", "GBP (£)", "CHF (CHF)")
    val languages = listOf("Français", "English")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // --- SECTION 1 : SÉCURITÉ ---
            SettingsSectionHeader(title = "Sécurité", icon = Icons.Default.Lock)

            // Biométrie
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Déverrouillage biométrique", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Utiliser l'empreinte ou le visage",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { isBiometricEnabled = it }
                    )
                }
            }

            // Code PIN
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Code PIN à 4 chiffres", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (userPin.isNotEmpty()) "Code PIN configuré" else "Non configuré",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showPinDialog = true
                            } else {
                                isPinEnabled = false
                                userPin = ""
                            }
                        }
                    )
                }
            }

            // --- SECTION 2 : PRÉFÉRENCES ---
            SettingsSectionHeader(title = "Préférences", icon = Icons.Default.Settings)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Devise
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currencyMenuExpanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Devise principale", fontWeight = FontWeight.SemiBold)
                        Box {
                            Text(selectedCurrency, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            DropdownMenu(
                                expanded = currencyMenuExpanded,
                                onDismissRequest = { currencyMenuExpanded = false }
                            ) {
                                currencies.forEach { currency ->
                                    DropdownMenuItem(
                                        text = { Text(currency) },
                                        onClick = {
                                            selectedCurrency = currency
                                            currencyMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Langue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageMenuExpanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Langue de l'application", fontWeight = FontWeight.SemiBold)
                        Box {
                            Text(selectedLanguage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        onClick = {
                                            selectedLanguage = lang
                                            languageMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 3 : COMPTE & IDENTIFIANTS ---
            SettingsSectionHeader(title = "Compte Firebase", icon = Icons.Default.Person)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("Changer d'adresse e-mail") },
                        supportingContent = { Text(auth.currentUser?.email ?: "Non connecté") },
                        trailingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.clickable { showEmailDialog = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Changer le mot de passe") },
                        supportingContent = { Text("Envoyer un e-mail de réinitialisation") },
                        trailingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.clickable { showPasswordDialog = true }
                    )
                }
            }

            // --- SECTION 4 : SAUVEGARDE & EXPORT ---
            SettingsSectionHeader(title = "Données & Sauvegarde", icon = Icons.Default.Share)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { exportToCSV(context, accounts) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV")
                }

                OutlinedButton(
                    onClick = { exportToJSON(context, accounts) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export JSON")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- DÉCONNEXION ---
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Se déconnecter")
            }
        }
    }

    // --- MODALES ET DIALOGS ---

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Définir un code PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4) pinInput = it },
                    label = { Text("Code à 4 chiffres") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            userPin = pinInput
                            isPinEnabled = true
                            showPinDialog = false
                            Toast.makeText(context, "Code PIN enregistré", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Le PIN doit contenir 4 chiffres", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    isPinEnabled = userPin.isNotEmpty()
                }) { Text("Annuler") }
            }
        )
    }

    if (showEmailDialog) {
        var newEmail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Nouveau e-mail") },
            text = {
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("Nouvelle adresse e-mail") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    auth.currentUser?.verifyBeforeUpdateEmail(newEmail.trim())
                        ?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "E-mail de confirmation envoyé à $newEmail", Toast.LENGTH_LONG).show()
                                showEmailDialog = false
                            } else {
                                Toast.makeText(context, "Erreur : ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                }) { Text("Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { showEmailDialog = false }) { Text("Annuler") } }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Réinitialiser le mot de passe") },
            text = { Text("Un lien de réinitialisation sera envoyé à : ${auth.currentUser?.email}") },
            confirmButton = {
                Button(onClick = {
                    auth.currentUser?.email?.let { email ->
                        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "E-mail de réinitialisation envoyé !", Toast.LENGTH_LONG).show()
                            }
                            showPasswordDialog = false
                        }
                    }
                }) { Text("Envoyer") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// --- LOGIQUE D'EXPORTATION DE FICHIERS ---

fun exportToCSV(context: Context, accounts: List<Account>) {
    if (accounts.isEmpty()) {
        Toast.makeText(context, "Aucun compte à exporter.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val csvData = StringBuilder()
        csvData.append("Nom du compte;Solde initial\n")
        accounts.forEach { account ->
            csvData.append("${account.name};${account.initialBalance}\n")
        }

        val fileName = "kaptal_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(csvData.toString().toByteArray()) }

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Sauvegarde Kaptal - CSV")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Exporter le fichier CSV via..."))

    } catch (e: Exception) {
        Toast.makeText(context, "Erreur lors de l'export CSV : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun exportToJSON(context: Context, accounts: List<Account>) {
    if (accounts.isEmpty()) {
        Toast.makeText(context, "Aucun compte à exporter.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("[\n")
        accounts.forEachIndexed { index, account ->
            jsonBuilder.append("  {\n")
            jsonBuilder.append("    \"name\": \"${account.name}\",\n")
            jsonBuilder.append("    \"initialBalance\": ${account.initialBalance}\n")
            jsonBuilder.append("  }")
            if (index < accounts.size - 1) jsonBuilder.append(",")
            jsonBuilder.append("\n")
        }
        jsonBuilder.append("]")

        val fileName = "kaptal_export_${System.currentTimeMillis()}.json"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(jsonBuilder.toString().toByteArray()) }

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Sauvegarde Kaptal - JSON")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Exporter le fichier JSON via..."))

    } catch (e: Exception) {
        Toast.makeText(context, "Erreur lors de l'export JSON : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}