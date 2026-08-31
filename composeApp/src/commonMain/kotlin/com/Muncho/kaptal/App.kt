package com.muncho.kaptal

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
import com.muncho.kaptal.screens.*
import com.muncho.kaptal.ui.theme.KaptalTheme
import com.muncho.kaptal.viewmodel.MainViewModel
import com.muncho.kaptal.viewmodel.SettingsViewModel
import com.muncho.kaptal.viewmodel.AccountDetailViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun App() {
    KaptalTheme {
        val navController = rememberNavController()
        val auth = Firebase.auth
        val mainViewModel: MainViewModel = viewModel { MainViewModel() }
        val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() }
        val platform = getPlatform()

        LaunchedEffect(Unit) {
            settingsViewModel.languageChangedEvent.collect {
                platform.setLanguage(settingsViewModel.selectedLanguage)
            }
        }
        
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
                    val scope = rememberCoroutineScope()
                    HomeScreen(
                        viewModel = mainViewModel,
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToAddAccount = { typeKey -> navController.navigate("add_account/$typeKey") },
                        onNavigateToEditAccount = { accountId -> navController.navigate("edit_account/$accountId") },
                        onAccountClick = { account ->
                            navController.navigate("account_detail/${account.id}")
                        }
                    )
                }

                composable(
                    route = "edit_account/{accountId}",
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                    val uiState by mainViewModel.uiState.collectAsState()
                    val accounts = (uiState as? com.muncho.kaptal.viewmodel.AccountsUiState.Success)?.accounts ?: emptyList()
                    val account = accounts.find { it.id == accountId }
                    
                    if (account != null) {
                        AddAccountScreen(
                            onBackClick = { navController.popBackStack() },
                            viewModel = mainViewModel,
                            accountToEdit = account,
                            onAccountAdded = { updatedAccount, memberEmail ->
                            mainViewModel.updateAccount(updatedAccount) {
                                if (!memberEmail.isNullOrBlank()) {
                                    mainViewModel.addMemberToAccount(updatedAccount.id, memberEmail) { success, msg ->
                                        if (!success) platform.showToast(msg)
                                        navController.popBackStack()
                                    }
                                } else {
                                    navController.popBackStack()
                                }
                            }
                        }
                        )
                    }
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
                            mainViewModel.addAccount(account, memberEmail) {
                                navController.popBackStack()
                            }
                        }
                    )
                }

                composable("settings") {
                    val uiState by mainViewModel.uiState.collectAsState()
                    val accounts = (uiState as? com.muncho.kaptal.viewmodel.AccountsUiState.Success)?.accounts ?: emptyList()
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavigateToCategories = { navController.navigate("category_management") },
                        allAccounts = accounts,
                        onExportClick = { account, format ->
                            if (account == null) {
                                mainViewModel.exportAllAccountsData(format)
                            } else {
                                mainViewModel.exportAccountData(account, format)
                            }
                        },
                        viewModel = settingsViewModel
                    )
                }

                composable("category_management") {
                    CategoryManagementScreen(
                        onBackClick = { navController.popBackStack() },
                        viewModel = settingsViewModel
                    )
                }

                composable(
                    route = "account_detail/{accountId}",
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                    val uiState by mainViewModel.uiState.collectAsState()
                    val accounts = (uiState as? com.muncho.kaptal.viewmodel.AccountsUiState.Success)?.accounts ?: emptyList()
                    val account = accounts.find { it.id == accountId }
                    val detailViewModel: AccountDetailViewModel = viewModel { AccountDetailViewModel() }

                    account?.let {
                        when (it.type) {
                            "CREDIT" -> CreditAccountScreen(
                                account = it,
                                allAccounts = accounts,
                                userCategories = settingsViewModel.userCategories,
                                onBackClick = { navController.popBackStack() },
                                mainViewModel = mainViewModel,
                                detailViewModel = detailViewModel
                            )
                            "CRYPTO" -> CryptoScreen(
                                account = it,
                                userCategories = settingsViewModel.userCategories,
                                onBackClick = { navController.popBackStack() },
                                mainViewModel = mainViewModel,
                                detailViewModel = detailViewModel
                            )
                            else -> StandardAccountScreen(
                                account = it,
                                allAccounts = accounts,
                                userCategories = settingsViewModel.userCategories,
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
}
