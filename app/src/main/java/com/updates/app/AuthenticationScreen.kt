package com.updates.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

private val AuthNavy = Color(0xFF071A57)
private val AuthBlue = Color(0xFF155EEF)
private val AuthPage = Color(0xFFF5F7FC)

@Composable
fun AuthGate(content: @Composable (FirebaseUser, () -> Unit) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    when {
        user == null -> AuthenticationScreen(auth)
        user?.isEmailVerified == true -> content(user!!) { auth.signOut() }
        else -> VerificationScreen(auth, user!!)
    }
}

@Composable
private fun AuthenticationScreen(auth: FirebaseAuth) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(AuthPage).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(76.dp).background(AuthNavy, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Text("U", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (isRegister) "Create your account" else "Welcome back", color = AuthNavy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Build Your Own Treasure of Knowledge.", color = AuthBlue, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = email, onValueChange = { email = it.trim(); message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp))
        if (isRegister) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp))
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF16834A), fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    message = "Enter a valid email address."; isError = true
                } else if (password.length < 6) {
                    message = "Password must contain at least 6 characters."; isError = true
                } else if (isRegister && password != confirmPassword) {
                    message = "Passwords do not match."; isError = true
                } else {
                    busy = true; message = ""; isError = false
                    val task = if (isRegister) auth.createUserWithEmailAndPassword(email, password) else auth.signInWithEmailAndPassword(email, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            if (isRegister) {
                                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { sent ->
                                    message = if (sent.isSuccessful) "Verification email sent. Open it, verify your address, then return here." else "Account created, but verification email could not be sent. Tap resend on the next screen."
                                    isError = !sent.isSuccessful
                                }
                            }
                        } else {
                            message = result.exception?.localizedMessage ?: "Authentication failed. Please try again."
                            isError = true
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AuthBlue)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (isRegister) "Create account" else "Log in", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { isRegister = !isRegister; message = "" }) {
            Text(if (isRegister) "Already have an account? Log in" else "New to UPDATES? Create account")
        }
        if (!isRegister) {
            TextButton(onClick = {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    message = "Enter your email address first."; isError = true
                } else {
                    auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                        message = if (task.isSuccessful) "Password reset email sent." else task.exception?.localizedMessage ?: "Could not send reset email."
                        isError = !task.isSuccessful
                    }
                }
            }) { Text("Forgot password?") }
        }
        Text("Your password is handled securely by Firebase.", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun VerificationScreen(auth: FirebaseAuth, user: FirebaseUser) {
    var message by remember { mutableStateOf("Please verify your email address to continue.") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(AuthPage).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = AuthBlue, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text("Verify your email", color = AuthNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(user.email ?: "", color = AuthBlue, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.DarkGray, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            busy = true
            user.reload().addOnCompleteListener {
                busy = false
                message = if (auth.currentUser?.isEmailVerified == true) "Email verified. Loading your account…" else "Not verified yet. Open the email link, then tap I've verified."
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("I've verified my email") }
        OutlinedButton(onClick = {
            busy = true
            user.sendEmailVerification().addOnCompleteListener { task ->
                busy = false
                message = if (task.isSuccessful) "A new verification email has been sent." else task.exception?.localizedMessage ?: "Could not resend email."
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Resend verification email") }
        TextButton(onClick = { auth.signOut() }) { Text("Use another account") }
    }
}
