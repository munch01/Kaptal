package com.example.kaptal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth

private const val PREFS_NAME = "kaptal_settings_prefs"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
private const val KEY_SELECTED_LANGUAGE = "selected_language"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    // Instanciation de SharedPreferences
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // États locaux
    var biometricEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    }
    var selectedLanguage by remember {
        mutableStateOf(sharedPreferences.getString(KEY_SELECTED_LANGUAGE, "Français") ?: "Français")
    }

    // Dialogues
    var showEmailDialog by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var isLoadingEmail by remember { mutableStateOf(false) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var isLoadingPassword by remember { mutableStateOf(false) }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isLoadingDelete by remember { mutableStateOf(false) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val languages = listOf("Français", "English", "Español")

    // Authentification biométrique pour activation
    fun authenticateBiometric(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Sécurisation de Kaptal")
                    .setSubtitle("Vérifiez votre identité pour activer la biométrie")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                        Toast.makeText(context, "Biométrie activée avec succès", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(context, "Erreur : $errString", Toast.LENGTH_SHORT).show()
                    }
                })

                biometricPrompt.authenticate(promptInfo)
            } else {
                Toast.makeText(context, "Impossible de démarrer l'authentification", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Aucune empreinte ou verrouillage configuré sur cet appareil", Toast.LENGTH_LONG).show()
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- SECTION COMPTE & SÉCURITÉ ---
            SectionTitle("Compte & Sécurité")

            SettingItem(
                icon = Icons.Default.Email,
                title = "Changer d'adresse e-mail",
                subtitle = currentUser?.email ?: "Non connecté",
                onClick = { showEmailDialog = true }
            )

            SettingItem(
                icon = Icons.Default.Lock,
                title = "Changer le mot de passe",
                subtitle = "Envoyer un e-mail de réinitialisation",
                onClick = { showPasswordDialog = true }
            )

            SettingSwitchItem(
                icon = Icons.Default.Fingerprint,
                title = "Code PIN & Biométrie",
                subtitle = "Sécuriser l'accès à l'application",
                checked = biometricEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        authenticateBiometric {
                            biometricEnabled = true
                            sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
                        }
                    } else {
                        biometricEnabled = false
                        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
                    }
                }
            )

            SettingItem(
                icon = Icons.Default.DeleteForever,
                title = "Supprimer le compte",
                subtitle = "Supprimer définitivement votre compte Kaptal",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { showDeleteAccountDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- SECTION PRÉFÉRENCES ---
            SectionTitle("Préférences")

            SettingItem(
                icon = Icons.Default.Language,
                title = "Langue",
                subtitle = selectedLanguage,
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- SECTION À PROPOS ---
            SectionTitle("À propos")

            SettingItem(
                icon = Icons.Default.Info,
                title = "À propos de Kaptal",
                subtitle = "Version 1.0.0 • Développé par Muncho",
                onClick = { showAboutDialog = true }
            )

            SettingItem(
                icon = Icons.Default.Code,
                title = "Code Source (GitHub)",
                subtitle = "Projet Open Source disponible sur GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Emeric16/Kaptal"))
                    context.startActivity(intent)
                }
            )
        }
    }

    // --- DIALOGUE 1 : CHANGER EMAIL ---
    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Changer l'adresse e-mail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saisissez votre nouvelle adresse e-mail.")
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Nouvel e-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isLoadingEmail && newEmail.isNotBlank(),
                    onClick = {
                        isLoadingEmail = true
                        currentUser?.verifyBeforeUpdateEmail(newEmail.trim())
                            ?.addOnCompleteListener { task ->
                                isLoadingEmail = false
                                showEmailDialog = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "E-mail de confirmation envoyé !", Toast.LENGTH_LONG).show()
                                    newEmail = ""
                                } else {
                                    Toast.makeText(context, "Erreur : ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                ) {
                    if (isLoadingEmail) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Mettre à jour")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- DIALOGUE 2 : RÉINITIALISATION DU MOT DE PASSE ---
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Réinitialiser le mot de passe") },
            text = {
                Text("Un e-mail de réinitialisation sera envoyé à :\n\n${currentUser?.email ?: ""}")
            },
            confirmButton = {
                Button(
                    enabled = !isLoadingPassword && currentUser?.email != null,
                    onClick = {
                        isLoadingPassword = true
                        auth.sendPasswordResetEmail(currentUser!!.email!!)
                            .addOnCompleteListener { task ->
                                isLoadingPassword = false
                                showPasswordDialog = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "E-mail envoyé !", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Erreur : ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                ) {
                    if (isLoadingPassword) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Envoyer")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- DIALOGUE 3 : SUPPRESSION DU COMPTE ---
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Supprimer le compte ?") },
            text = {
                Text("Cette action est irréversible. Toutes vos données seront définitivement effacées de Firebase.")
            },
            confirmButton = {
                Button(
                    enabled = !isLoadingDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        isLoadingDelete = true
                        currentUser?.delete()
                            ?.addOnCompleteListener { task ->
                                isLoadingDelete = false
                                showDeleteAccountDialog = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Compte supprimé avec succès", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Erreur (reconnexion requise) : ${task.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                ) {
                    if (isLoadingDelete) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Supprimer définitivement")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- DIALOGUE 4 : SÉLECTION DE LA LANGUE ---
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Choisir la langue") },
            text = {
                Column(Modifier.selectableGroup()) {
                    languages.forEach { language ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (language == selectedLanguage),
                                    onClick = {
                                        selectedLanguage = language
                                        sharedPreferences.edit().putString(KEY_SELECTED_LANGUAGE, language).apply()
                                        showLanguageDialog = false
                                    },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (language == selectedLanguage),
                                onClick = null
                            )
                            Text(
                                text = language,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- DIALOGUE 5 : À PROPOS ---
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("À propos de Kaptal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version : 1.0.0")
                    Text("Développé par Muncho.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Code source libre de droit. Ce projet est open source et disponible pour la communauté.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (textColor == MaterialTheme.colorScheme.error) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}