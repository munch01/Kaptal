package com.example.kaptal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kaptal.ui.theme.KaptalTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaptalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KaptalNavigation()
                }
            }
        }
    }
}

@Composable
fun KaptalNavigation() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    // Si connecté -> "home", sinon -> "auth"
    val startDestination = if (auth.currentUser != null) "home" else "auth"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // --- ÉCRAN D'AUTHENTIFICATION ---
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // --- ÉCRAN PRINCIPAL (MES COMPTES) ---
        composable("home") {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        // --- ÉCRAN DE PARAMÈTRES ---
        composable("settings") {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}