package com.example.kaptal

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Fonction pour calculer la force du mot de passe (retourne un score de 0 à 4)
    val passwordStrength = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    fun saveUserToFirestore(onComplete: () -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.email != null) {
            val userId = currentUser.uid
            val userEmail = currentUser.email!!.trim().lowercase()

            val userMap = hashMapOf("email" to userEmail)

            firestore.collection("users")
                .document(userId)
                .set(userMap, SetOptions.merge())
                .addOnCompleteListener { onComplete() }
        } else {
            onComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kaptal",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSignUp) "Créer un compte" else "Connexion",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Adresse e-mail") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                val description = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Indicateur de force affiché uniquement lors de la création de compte
        AnimatedVisibility(visible = isSignUp) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Force du mot de passe :", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = when (passwordStrength) {
                            0, 1 -> "Faible"
                            2 -> "Moyen"
                            3 -> "Bon"
                            else -> "Très sécurisé"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = when (passwordStrength) {
                            0, 1 -> Color.Red
                            2 -> Color(0xFFFF9800) // Orange
                            3 -> Color(0xFF8BC34A) // Vert clair
                            else -> Color(0xFF4CAF50) // Vert
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { passwordStrength / 4f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when (passwordStrength) {
                        0, 1 -> Color.Red
                        2 -> Color(0xFFFF9800)
                        3 -> Color(0xFF8BC34A)
                        else -> Color(0xFF4CAF50)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (isSignUp && passwordStrength < 2) {
                    Toast.makeText(context, "Veuillez choisir un mot de passe un peu plus sécurisé", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                saveUserToFirestore {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Erreur : ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password.trim())
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                saveUserToFirestore {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Échec de connexion : ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isSignUp) "S'inscrire" else "Se connecter", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(
                if (isSignUp) "Déjà un compte ? Se connecter" else "Pas de compte ? S'inscrire"
            )
        }
    }
}