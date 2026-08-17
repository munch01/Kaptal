package com.Muncho.kaptal

import android.app.Application
import android.content.Context
import com.Muncho.kaptal.model.Account
import com.Muncho.kaptal.model.CategoryFamily
import com.Muncho.kaptal.model.getDefaultCategories
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE)

    private val _languageChangedEvent = MutableSharedFlow<Unit>()
    val languageChangedEvent = _languageChangedEvent.asSharedFlow()

    // --- ÉTATS OBSERVABLES PAR L'UI ---
    var isBiometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", false))
        private set

    var selectedCurrency by mutableStateOf(prefs.getString("selected_currency", "EUR (€)") ?: "EUR (€)")
        private set

    var selectedLanguage by mutableStateOf(prefs.getString("selected_language", "Français") ?: "Français")
        private set

    var userCategories = mutableStateListOf<CategoryFamily>()
        private set

    val currentUser get() = auth.currentUser

    init {
        // Au démarrage du ViewModel, on écoute les changements Firebase en direct
        listenToFirebaseUserData()
    }

    /**
     * Écoute en temps réel le document Firestore de l'utilisateur.
     * Si une donnée change sur le serveur, l'UI se met à jour instantanément.
     */
    private fun listenToFirebaseUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Mise à jour de la devise depuis le Cloud
                snapshot.getString("currency")?.let { cloudCurrency ->
                    if (cloudCurrency != selectedCurrency) {
                        selectedCurrency = cloudCurrency
                        prefs.edit().putString("selected_currency", cloudCurrency).apply()
                    }
                }

                // Mise à jour de la langue depuis le Cloud
                snapshot.getString("language")?.let { cloudLanguage ->
                    if (cloudLanguage != selectedLanguage) {
                        selectedLanguage = cloudLanguage
                        prefs.edit().putString("selected_language", cloudLanguage).apply()
                    }
                }

                // Mise à jour de l'option biométrique depuis le Cloud
                snapshot.getBoolean("biometricEnabled")?.let { cloudBiometric ->
                    if (cloudBiometric != isBiometricEnabled) {
                        isBiometricEnabled = cloudBiometric
                        prefs.edit().putBoolean("biometric_enabled", cloudBiometric).apply()
                    }
                }

                // Mise à jour des catégories depuis le Cloud
                val cloudCategories = snapshot.get("categories") as? List<Map<String, Any>>
                if (cloudCategories != null) {
                    val families = cloudCategories.map { map ->
                        CategoryFamily(
                            name = map["name"] as? String ?: "",
                            subCategories = map["subCategories"] as? List<String> ?: emptyList()
                        )
                    }
                    if (families != userCategories.toList()) {
                        userCategories.clear()
                        userCategories.addAll(families)
                    }
                } else if (userCategories.isEmpty()) {
                    // Initialisation si vide
                    val defaults = getDefaultCategories()
                    userCategories.addAll(defaults)
                    syncUserSettingsToFirebase(mapOf("categories" to defaults))
                }
            }
    }

    // --- ACTIONS DÉCLENCHÉES PAR L'UI ---

    fun updateCurrency(newCurrency: String) {
        selectedCurrency = newCurrency
        prefs.edit().putString("selected_currency", newCurrency).apply()
        syncUserSettingsToFirebase(mapOf("currency" to newCurrency))
    }

    fun updateLanguage(newLanguage: String) {
        if (selectedLanguage != newLanguage) {
            selectedLanguage = newLanguage
            prefs.edit().putString("selected_language", newLanguage).apply()
            syncUserSettingsToFirebase(mapOf("language" to newLanguage))
            viewModelScope.launch {
                _languageChangedEvent.emit(Unit)
            }
        }
    }

    fun updateBiometric(enabled: Boolean) {
        isBiometricEnabled = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        syncUserSettingsToFirebase(mapOf("biometricEnabled" to enabled))
    }

    // --- GESTION DES CATÉGORIES ---
    
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
        val email = currentUser?.email
        if (email.isNullOrEmpty()) {
            onResult(false, "Adresse email introuvable.")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(true, "E-mail envoyé à $email") }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Erreur") }
    }

    fun updateEmail(newEmail: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser
        if (user == null) {
            onResult(false, "Utilisateur non connecté.")
            return
        }

        user.verifyBeforeUpdateEmail(newEmail.trim())
            .addOnSuccessListener { onResult(true, "E-mail de vérification envoyé à ${newEmail.trim()}") }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Erreur lors de la mise à jour") }
    }

    fun signOut() {
        auth.signOut()
    }

    fun deleteAccount(onResult: (Boolean, String) -> Unit) {
        currentUser?.delete()
            ?.addOnSuccessListener { onResult(true, "Compte supprimé avec succès.") }
            ?.addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Erreur") }
    }

    fun getExportDataJson(accounts: List<Account>, balances: Map<String, Double>): String {
        val root = JSONObject()
        val accountsArray = org.json.JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("name", acc.name)
            obj.put("bank", acc.bankName)
            obj.put("balance", balances[acc.id] ?: acc.initialBalance)
            obj.put("type", acc.type)
            accountsArray.put(obj)
        }
        root.put("accounts", accountsArray)
        root.put("exportDate", Timestamp.now().toDate().toString())
        return root.toString(4)
    }

    fun getExportDataCsv(accounts: List<Account>, balances: Map<String, Double>): String {
        val sb = StringBuilder()
        sb.append("Nom du compte;Banque;Type;Solde actuel\n")
        accounts.forEach { acc ->
            val balance = balances[acc.id] ?: acc.initialBalance
            sb.append("${acc.name};${acc.bankName};${acc.type};${String.format("%.2f", balance)}\n")
        }
        return sb.toString()
    }

    /**
     * Envoie générique vers Firestore avec merge pour préserver les autres champs
     */
    private fun syncUserSettingsToFirebase(data: Map<String, Any>) {
        val userId = auth.currentUser?.uid ?: return

        val updates = data.toMutableMap()
        updates["lastUpdated"] = Timestamp.now()

        db.collection("users").document(userId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                println("Synchro Firebase réussie : $data")
            }
            .addOnFailureListener { e ->
                println("Erreur de synchro Firebase : ${e.localizedMessage}")
            }
    }
}