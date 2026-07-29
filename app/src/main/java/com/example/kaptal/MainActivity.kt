package com.example.kaptal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.kaptal.ui.theme.KaptalTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        enableEdgeToEdge()

        setContent {
            KaptalTheme {
                var currentUser by remember { mutableStateOf(auth.currentUser) }
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                val accounts = remember { mutableStateListOf<Account>() }

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

                    // 1. Redirection vers la connexion si l'utilisateur est déconnecté
                    if (currentUser == null) {
                        AuthScreen(
                            onAuthSuccess = {
                                currentUser = auth.currentUser
                                currentScreen = Screen.Home
                            },
                            modifier = modifier
                        )
                    } else {
                        // 2. Navigation principale quand l'utilisateur est connecté
                        when (currentScreen) {
                            is Screen.Home -> {
                                HomeScreen(
                                    accounts = accounts,
                                    onNavigateToAddAccount = { currentScreen = Screen.AddAccount },
                                    onNavigateToSettings = { currentScreen = Screen.Settings },
                                    onLogoutClick = { auth.signOut() },
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
                                    onBackClick = { currentScreen = Screen.Home },
                                    onLogoutClick = {
                                        auth.signOut()
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Home : Screen()
    object AddAccount : Screen()
    object Settings : Screen()
}