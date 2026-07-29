package com.example.kaptal

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.kaptal.ui.theme.KaptalTheme
import com.google.firebase.auth.FirebaseAuth

private const val PREFS_NAME = "kaptal_settings_prefs"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

class MainActivity : FragmentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        enableEdgeToEdge()

        setContent {
            KaptalTheme {
                val context = this
                val sharedPreferences = remember {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }

                val isBiometricConfigured = remember {
                    sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
                }

                var currentUser by remember { mutableStateOf(auth.currentUser) }
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                val accounts = remember { mutableStateListOf<Account>() }

                // État pour savoir si l'application est déverrouillée
                var isUnlocked by remember { mutableStateOf(!isBiometricConfigured) }

                // Déclencher l'empreinte biométrique si activée
                LaunchedEffect(Unit) {
                    if (isBiometricConfigured && currentUser != null) {
                        checkBiometricLock(
                            onSuccess = { isUnlocked = true },
                            onError = { isUnlocked = false }
                        )
                    }
                }

                // Observer l'état de connexion Firebase
                DisposableEffect(auth) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        currentUser = firebaseAuth.currentUser
                    }
                    auth.addAuthStateListener(listener)
                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)

                    if (currentUser == null) {
                        // 1. Écran d'authentification Firebase
                        AuthScreen(
                            onAuthSuccess = {
                                currentUser = auth.currentUser
                                currentScreen = Screen.Home
                                isUnlocked = true
                            },
                            modifier = modifier
                        )
                    } else if (!isUnlocked) {
                        // 2. Écran de verrouillage si la biométrie est requise
                        LockedScreen(
                            onRetryClick = {
                                checkBiometricLock(
                                    onSuccess = { isUnlocked = true },
                                    onError = { isUnlocked = false }
                                )
                            },
                            modifier = modifier
                        )
                    } else {
                        // 3. Navigation principale dans l'application
                        when (currentScreen) {
                            is Screen.Home -> {
                                HomeScreen(
                                    accounts = accounts,
                                    onNavigateToAddAccount = { currentScreen = Screen.AddAccount },
                                    onNavigateToSettings = { currentScreen = Screen.Settings },
                                    onLogoutClick = {
                                        finishAffinity()
                                    },
                                    modifier = modifier
                                )
                            }
                            is Screen.AddAccount -> {
                                AddAccountScreen(
                                    onBackClick = { currentScreen = Screen.Home },
                                    onAccountAdded = { newAccount ->
                                        accounts.add(newAccount)
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                            is Screen.Settings -> {
                                SettingsScreen(
                                    onBackClick = { currentScreen = Screen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkBiometricLock(onSuccess: () -> Unit, onError: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Kaptal est verrouillé")
                .setSubtitle("Veuillez vous authentifier pour accéder à vos finances")
                .setAllowedAuthenticators(authenticators)
                .build()

            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Échec : $errString", Toast.LENGTH_SHORT).show()
                    onError()
                }
            })

            biometricPrompt.authenticate(promptInfo)
        } else {
            onSuccess()
        }
    }
}

// Écran de verrouillage
@Composable
private fun LockedScreen(onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Kaptal est verrouillé",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(onClick = onRetryClick) {
                Text("Déverrouiller avec l'empreinte")
            }
        }
    }
}

sealed class Screen {
    object Home : Screen()
    object AddAccount : Screen()
    object Settings : Screen()
}