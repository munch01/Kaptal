package com.muncho.kaptal.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.getPlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auth = Firebase.auth
    val firestore = Firebase.firestore
    val platform = getPlatform()
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val errorFieldsBlank = stringResource(Res.string.auth_error_fields_blank)
    val errorPasswordWeak = stringResource(Res.string.auth_error_password_weak)

    val passwordStrength = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    fun saveUserToFirestore(onComplete: () -> Unit) {
        coroutineScope.launch {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.email != null) {
                val userId = currentUser.uid
                val userEmail = currentUser.email!!.trim().lowercase()
                val userMap = mapOf("email" to userEmail)
                try {
                    firestore.collection("users").document(userId).set(userMap) // merge is default or via different overload
                } catch (e: Exception) {}
            }
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        Image(
            painter = painterResource(Res.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_kaptal_logo),
                contentDescription = "Logo Kaptal",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isSignUp) {
                Text(
                    text = stringResource(Res.string.auth_signup_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.auth_email_label)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.auth_password_label)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = isSignUp) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(Res.string.auth_password_strength), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = when (passwordStrength) {
                                0, 1 -> stringResource(Res.string.auth_password_weak)
                                2 -> stringResource(Res.string.auth_password_medium)
                                3 -> stringResource(Res.string.auth_password_good)
                                else -> stringResource(Res.string.auth_password_strong)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when (passwordStrength) {
                                0, 1 -> Color.Red
                                2 -> Color(0xFFFF9800)
                                3 -> Color(0xFF8BC34A)
                                else -> Color(0xFF4CAF50)
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
                        platform.showToast(errorFieldsBlank)
                        return@Button
                    }
                    if (isSignUp && passwordStrength < 2) {
                        platform.showToast(errorPasswordWeak)
                        return@Button
                    }

                    isLoading = true
                    val cleanEmail = email.trim().lowercase()
                    val cleanPassword = password.trim()
                    
                    coroutineScope.launch {
                        try {
                            if (isSignUp) {
                                auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                                saveUserToFirestore {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            } else {
                                auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                                saveUserToFirestore {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            coroutineScope.launch {
                                val msg = getString(Res.string.common_error_prefix, e.message ?: "")
                                platform.showToast(msg)
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
                    Text(if (isSignUp) stringResource(Res.string.auth_signup_button) else stringResource(Res.string.auth_login_button), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    if (isSignUp) stringResource(Res.string.auth_go_to_login) else stringResource(Res.string.auth_go_to_signup)
                )
            }
        }
    }
}
