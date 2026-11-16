package com.meeshox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meeshox.app.data.Outcome
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.theme.BrandGradient

@Composable
fun AuthScreen(vm: ShopViewModel) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(BrandGradient), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MEESHO X", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("Smarter shopping for Bharat", color = Color.White.copy(alpha = 0.85f))
            }
        }
        Card(Modifier.padding(16.dp).offset(y = (-40).dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge)
                if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { if (it.length <= 10) phone = it.filter(Char::isDigit) }, label = { Text("Mobile number") }, prefix = { Text("+91 ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (register) OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (register) Text("8+ characters, letters and numbers") })
                if (register) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, { consent = it })
                    Text("I agree to the Terms and Privacy Policy and consent to my data being stored on this device.", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        if (register && !consent) { error = "Please accept the privacy consent"; return@Button }
                        loading = true; error = null
                        vm.io {
                            val r = if (register) vm.repo.register(name, phone, email, password) else vm.repo.login(phone, password)
                            if (register && r is Outcome.Ok) vm.repo.prefs.setConsent(true)
                            loading = false
                            if (r is Outcome.Err) error = r.message else vm.refreshRecommendations()
                        }
                    },
                    enabled = !loading, modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text(if (register) "Create account" else "Log in") }
                TextButton(onClick = { register = !register; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Already have an account? Log in" else "New to MEESHO X? Create an account")
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Text("OTP login: the architecture is ready (see docs), but no SMS provider is configured in this build, so sign-in uses a password stored as a salted PBKDF2 hash on this device.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
