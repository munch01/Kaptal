package com.example.kaptal.data

import com.example.kaptal.model.Account
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Pseudonymisation RGPD : Toutes les données sont rattachées à l'UID anonyme Firebase Auth
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // --- GESTION DES COMPTES BANCAIRES ---

    /**
     * Ajouter un nouveau compte bancaire dans Cloud Firestore
     */
    suspend fun addAccount(account: Account): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        return try {
            db.collection("users")
                .document(uid)
                .collection("accounts")
                .add(account)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupérer tous les comptes bancaires de l'utilisateur connecté
     */
    suspend fun getAccounts(): Result<List<Account>> {
        val uid = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        return try {
            val snapshot = db.collection("users")
                .document(uid)
                .collection("accounts")
                .get()
                .await()

            val accounts = snapshot.toObjects(Account::class.java)
            Result.success(accounts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Supprimer un compte bancaire individuel
     */
    suspend fun deleteAccount(accountId: String): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        return try {
            db.collection("users")
                .document(uid)
                .collection("accounts")
                .document(accountId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CONFORMITÉ RGPD : DROIT À L'OUBLI ---

    /**
     * Supprimer l'intégralité des données Firestore de l'utilisateur.
     * Cette fonction efface toutes les sous-collections (comptes, transactions)
     * ainsi que le document utilisateur principal avant la suppression du compte Auth.
     */
    suspend fun deleteAllUserData(): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        return try {
            val userDocRef = db.collection("users").document(uid)

            // 1. Supprimer tous les comptes de la sous-collection "accounts"
            val accounts = userDocRef.collection("accounts").get().await()
            for (document in accounts.documents) {
                document.reference.delete().await()
            }

            // 2. Supprimer toutes les transactions de la sous-collection "transactions" (si présente)
            val transactions = userDocRef.collection("transactions").get().await()
            for (document in transactions.documents) {
                document.reference.delete().await()
            }

            // 3. Supprimer le document utilisateur racine
            userDocRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}