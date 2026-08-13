package com.Muncho.kaptal

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Muncho.kaptal.screens.CreditAccountScreen
import com.Muncho.kaptal.screens.CryptoScreen
import com.Muncho.kaptal.screens.StandardAccountScreen
import com.Muncho.kaptal.ui.theme.KaptalTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : FragmentActivity() {

    private lateinit var appUpdateManager: AppUpdateManager
    private val updateRequestCode = 1001

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("selected_language", "Français") ?: "Français"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()

        val prefs = getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("selected_language", "Français") ?: "Français"
        LocaleHelper.setLocale(this, lang)

        setContent {
            KaptalTheme {
                val context = LocalContext.current
                CompositionLocalProvider(
                    LocalActivity provides this@MainActivity,
                    LocalContext provides LocaleHelper.setLocale(context, lang)
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        delay(1200)
                        showSplash = false
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFE8ECEF)
                    ) {
                        if (showSplash) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.fond_kaptal_propre),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_k_logo),
                                    contentDescription = "Logo Kaptal",
                                    modifier = Modifier.size(160.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            KaptalApp(activity = this@MainActivity)
                        }
                    }
                }
            }
        }
    }

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    AppUpdateType.IMMEDIATE,
                    this,
                    updateRequestCode
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    AppUpdateType.IMMEDIATE,
                    this,
                    updateRequestCode
                )
            }
        }
    }
}

val LocalActivity = staticCompositionLocalOf<FragmentActivity> {
    error("No Activity found")
}

@Composable
fun KaptalApp(
    activity: FragmentActivity,
    viewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        settingsViewModel.languageChangedEvent.collect {
            activity.recreate()
        }
    }

    val prefs = remember { context.getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE) }
    val isBiometricEnabled = remember { prefs.getBoolean("biometric_enabled", false) }
    val hasSession = auth.currentUser != null

    var isUnlocked by remember { mutableStateOf(!hasSession || !isBiometricEnabled) }

    LaunchedEffect(Unit) {
        if (hasSession && isBiometricEnabled) {
            triggerAppUnlockBiometric(
                activity = activity,
                onSuccess = { isUnlocked = true },
                onError = {
                    Toast.makeText(context, context.getString(R.string.biometric_error_required), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val startDestination = if (hasSession) "home" else "auth"

    Box(modifier = Modifier.fillMaxSize()) {
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
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onAccountClick = { account ->
                        viewModel.selectAccount(account)
                        when (account.type) {
                            "CREDIT" -> navController.navigate("credit_detail")
                            "LIVRET_A" -> navController.navigate("livret_a_detail")
                            else -> navController.navigate("standard_detail")
                        }
                    },
                    onExit = { activity.finish() }
                )
            }

            composable("crypto") {
                CryptoScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("settings") {
                val uiState by viewModel.uiState.collectAsState()
                val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
                val allBalances = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accountBalances else emptyMap()

                SettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    allAccounts = allAccounts,
                    allBalances = allBalances,
                    viewModel = settingsViewModel
                )
            }

            composable("standard_detail") {
                val account by viewModel.selectedAccount.collectAsState()
                val uiState by viewModel.uiState.collectAsState()
                val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()
                
                account?.let { acc ->
                    StandardAccountScreen(
                        account = acc,
                        allAccounts = allAccounts,
                        initialPage = viewModel.getSavedPagerPosition(acc.id),
                        onPageChanged = { page ->
                            viewModel.savePagerPosition(acc.id, page)
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable("credit_detail") {
                val account by viewModel.selectedAccount.collectAsState()
                val uiState by viewModel.uiState.collectAsState()
                val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()

                account?.let {
                    CreditAccountScreen(
                        account = it,
                        allAccounts = allAccounts,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable("livret_a_detail") {
                val account by viewModel.selectedAccount.collectAsState()
                val uiState by viewModel.uiState.collectAsState()
                val allAccounts = if (uiState is AccountsUiState.Success) (uiState as AccountsUiState.Success).accounts else emptyList()

                account?.let { acc ->
                    StandardAccountScreen(
                        account = acc,
                        allAccounts = allAccounts,
                        initialPage = viewModel.getSavedPagerPosition(acc.id),
                        onPageChanged = { page ->
                            viewModel.savePagerPosition(acc.id, page)
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }

        if (!isUnlocked) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.lock_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                triggerAppUnlockBiometric(
                                    activity = activity,
                                    onSuccess = { isUnlocked = true },
                                    onError = { }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lock_unlock_button))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                auth.signOut()
                                isUnlocked = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lock_use_password_button))
                        }
                    }
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
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.cancel_label))
            .build()

        biometricPrompt.authenticate(promptInfo)
    } else {
        onSuccess()
    }
}
