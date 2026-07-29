package com.example.kaptal

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KaptalApp(activity = this)
                }
            }
        }
    }
}

@Composable
fun KaptalApp(activity: FragmentActivity) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = activity.applicationContext

    val prefs = remember { context.getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE) }
    val isBiometricEnabled = remember { prefs.getBoolean("biometric_enabled", false) }
    val hasSession = auth.currentUser != null

    // Si une session existe ET que la biométrie est activée -> on verrouille au démarrage
    var isUnlocked by remember { mutableStateOf(!hasSession || !isBiometricEnabled) }

    // Déclenché UNISUEMENT au lancement si la session est active
    LaunchedEffect(Unit) {
        if (hasSession && isBiometricEnabled) {
            triggerAppUnlockBiometric(
                activity = activity,
                onSuccess = { isUnlocked = true },
                onError = {
                    Toast.makeText(context, "Empreinte requise pour ouvrir l'application", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (isUnlocked) {
        val startDestination = if (hasSession) "home" else "auth"

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("auth") {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate("home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    } else {
        // Écran d'attente si la biométrie est annulée ou échouée au lancement
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Kaptal est verrouillé",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        triggerAppUnlockBiometric(
                            activity = activity,
                            onSuccess = { isUnlocked = true },
                            onError = { }
                        )
                    }
                ) {
                    Text("Déverrouiller avec l'empreinte")
                }
            }
        }
    }
}

private fun triggerAppUnlockBiometric(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    val biometricManager = BiometricManager.from(activity)

    if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouillage Kaptal")
            .setSubtitle("Scannez votre empreinte pour accéder à l'application")
            .setNegativeButtonText("Annuler")
            .build()

        biometricPrompt.authenticate(promptInfo)
    } else {
        onSuccess()
    }
}