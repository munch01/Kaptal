package com.Muncho.kaptal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Muncho.kaptal.getSettings
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.CategoryFamily
import com.Muncho.kaptal.model.getDefaultCategories
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val settings = getSettings()

    private val _languageChangedEvent = MutableSharedFlow<Unit>()
    val languageChangedEvent = _languageChangedEvent.asSharedFlow()

    var isBiometricEnabled by mutableStateOf(settings.getBoolean("biometric_enabled", false))
        private set

    var selectedCurrency by mutableStateOf(settings.getString("selected_currency", "EUR (€)"))
        private set

    var selectedLanguage by mutableStateOf(settings.getString("selected_language", "Français"))
        private set

    var userCategories = mutableStateListOf<CategoryFamily>()
        private set

    val currentUser get() = auth.currentUser

    init {
        listenToFirebaseUserData()
    }

    private fun listenToFirebaseUserData() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            db.collection("users").document(userId)
                .snapshots.collect { snapshot ->
                    if (!snapshot.exists) return@collect

                    snapshot.get<String>("currency").let { cloudCurrency ->
                        if (cloudCurrency.isNotBlank() && cloudCurrency != selectedCurrency) {
                            selectedCurrency = cloudCurrency
                            settings.putString("selected_currency", cloudCurrency)
                        }
                    }

                    snapshot.get<String>("language").let { cloudLanguage ->
                        if (cloudLanguage.isNotBlank() && cloudLanguage != selectedLanguage) {
                            selectedLanguage = cloudLanguage
                            settings.putString("selected_language", cloudLanguage)
                        }
                    }

                    // For booleans, GitLive get might return null if not present
                    try {
                        val cloudBiometric = snapshot.get<Boolean>("biometricEnabled")
                        if (cloudBiometric != isBiometricEnabled) {
                            isBiometricEnabled = cloudBiometric
                            settings.putBoolean("biometric_enabled", cloudBiometric)
                        }
                    } catch (e: Exception) {}

                    val cloudCategories = try { snapshot.get<List<CategoryFamily>>("categories") } catch (e: Exception) { null }
                    if (cloudCategories != null) {
                        if (cloudCategories != userCategories.toList()) {
                            userCategories.clear()
                            userCategories.addAll(cloudCategories)
                        }
                    } else if (userCategories.isEmpty()) {
                        val defaults = getDefaultCategories()
                        userCategories.addAll(defaults)
                        syncUserSettingsToFirebase(mapOf("categories" to defaults))
                    }
                }
        }
    }

    fun updateCurrency(newCurrency: String) {
        selectedCurrency = newCurrency
        settings.putString("selected_currency", newCurrency)
        syncUserSettingsToFirebase(mapOf("currency" to newCurrency))
    }

    fun updateLanguage(newLanguage: String) {
        if (selectedLanguage != newLanguage) {
            selectedLanguage = newLanguage
            settings.putString("selected_language", newLanguage)
            syncUserSettingsToFirebase(mapOf("language" to newLanguage))
            viewModelScope.launch {
                _languageChangedEvent.emit(Unit)
            }
        }
    }

    fun updateBiometric(enabled: Boolean) {
        isBiometricEnabled = enabled
        settings.putBoolean("biometric_enabled", enabled)
        syncUserSettingsToFirebase(mapOf("biometricEnabled" to enabled))
    }

    fun addCategoryFamily(name: String) {
        if (name.isBlank() || userCategories.any { it.name == name }) return
        val newList = userCategories.toMutableList()
        newList.add(CategoryFamily(name = name, subCategories = emptyList()))
        syncUserSettingsToFirebase(mapOf("categories" to newList))
    }

    fun deleteCategoryFamily(name: String) {
        val newList = userCategories.filter { it.name != name }
        syncUserSettingsToFirebase(mapOf("categories" to newList))
    }

    fun addSubCategory(familyName: String, subName: String) {
        if (subName.isBlank()) return
        val newList = userCategories.map { family ->
            if (family.name == familyName) {
                if (family.subCategories.contains(subName)) return@map family
                family.copy(subCategories = family.subCategories + subName)
            } else family
        }
        syncUserSettingsToFirebase(mapOf("categories" to newList))
    }

    fun deleteSubCategory(familyName: String, subName: String) {
        val newList = userCategories.map { family ->
            if (family.name == familyName) {
                family.copy(subCategories = family.subCategories.filter { it != subName })
            } else family
        }
        syncUserSettingsToFirebase(mapOf("categories" to newList))
    }

    fun renameFamily(oldName: String, newName: String) {
        if (newName.isBlank() || userCategories.any { it.name == newName }) return
        val newList = userCategories.map { family ->
            if (family.name == oldName) family.copy(name = newName) else family
        }
        syncUserSettingsToFirebase(mapOf("categories" to newList))
    }

    fun sendPasswordResetEmail(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val email = currentUser?.email
                if (email.isNullOrEmpty()) {
                    onResult(false, "Adresse email introuvable.")
                    return@launch
                }
                auth.sendPasswordResetEmail(email)
                onResult(true, "E-mail envoyé à $email")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erreur")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
        }
    }

    fun deleteAccount(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                currentUser?.delete()
                onResult(true, "Compte supprimé avec succès.")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erreur")
            }
        }
    }

    fun getExportDataJson(accounts: List<Account>, balances: Map<String, Double>): String {
        return Json.encodeToString(accounts) // Simplifié pour KMP
    }

    private fun syncUserSettingsToFirebase(data: Map<String, Any>) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = data.toMutableMap()
                updates["lastUpdated"] = Timestamp.now()
                db.collection("users").document(userId).update(updates)
            } catch (e: Exception) { }
        }
    }
}
