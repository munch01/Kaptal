package com.Muncho.kaptal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.Muncho.kaptal.screens.*
import com.Muncho.kaptal.ui.theme.KaptalTheme
import com.Muncho.kaptal.viewmodel.MainViewModel
import com.Muncho.kaptal.viewmodel.SettingsViewModel
import com.Muncho.kaptal.viewmodel.AccountDetailViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

@Composable
fun App() {
    KaptalTheme {
        val navController = rememberNavController()
        val auth = Firebase.auth
        val mainViewModel: MainViewModel = viewModel { MainViewModel() }
        val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() }
        
        val hasSession = auth.currentUser != null
        val startDestination = if (hasSession) "home" else "auth"

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFE8ECEF)
        ) {
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
                        viewModel = mainViewModel,
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToAddAccount = { typeKey -> navController.navigate("add_account/$typeKey") },
                        onAccountClick = { account ->
                            navController.navigate("account_detail/${account.id}")
                        }
                    )
                }

                composable(
                    route = "add_account/{typeKey}",
                    arguments = listOf(navArgument("typeKey") { type = NavType.StringType; defaultValue = "CHECKING" })
                ) { backStackEntry ->
                    val typeKey = backStackEntry.arguments?.getString("typeKey")
                    AddAccountScreen(
                        onBackClick = { navController.popBackStack() },
                        viewModel = mainViewModel,
                        initialTypeKey = typeKey,
                        onAccountAdded = { account, memberEmail ->
                            mainViewModel.addAccount(account) { accountId ->
                                if (memberEmail != null) {
                                    mainViewModel.addMemberToAccount(accountId, memberEmail) { _, _ -> }
                                }
                            }
                            navController.popBackStack()
                        }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavigateToCategories = { /* navigate to categories */ },
                        viewModel = settingsViewModel
                    )
                }

                composable(
                    route = "account_detail/{accountId}",
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                    val uiState by mainViewModel.uiState.collectAsState()
                    val accounts = (uiState as? com.Muncho.kaptal.viewmodel.AccountsUiState.Success)?.accounts ?: emptyList()
                    val account = accounts.find { it.id == accountId }
                    val detailViewModel: AccountDetailViewModel = viewModel { AccountDetailViewModel() }

                    account?.let {
                        StandardAccountScreen(
                            account = it,
                            allAccounts = accounts,
                            onBackClick = { navController.popBackStack() },
                            mainViewModel = mainViewModel,
                            detailViewModel = detailViewModel
                        )
                    }
                }
            }
        }
    }
}
