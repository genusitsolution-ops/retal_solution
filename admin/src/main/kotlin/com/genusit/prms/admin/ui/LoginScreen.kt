package com.genusit.prms.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.genusit.prms.admin.net.ApiException
import kotlinx.coroutines.launch

/** Super Admin sign-in. Owner, employee and tenant accounts are refused (the server also refuses their tokens). */
@Composable
fun LoginScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (user.isBlank() || pass.isBlank()) { error = "Enter your username and password."; return }
        busy = true; error = null
        scope.launch {
            try {
                val r = app.api.login(user.trim(), pass)
                if (r.userType != "admin") {
                    error = "This app is only for the Super Admin. Owners, employees and tenants use the Rental Solution app."
                } else {
                    pass = ""
                    app.session.save(r.token, r.user.fullName)
                }
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Something went wrong. Try again."
            } finally { busy = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.BrandDark, C.Brand))).imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(8.dp))
            Text("Rental Solution Admin", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Super Admin only", color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(user, { user = it; error = null }, label = { Text("Username or email") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it; error = null }, label = { Text("Password") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Hide password" else "Show password") } },
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = C.Danger, style = MaterialTheme.typography.bodyMedium) }
                    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) else Text("Sign in", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Private app for managing owners, approvals, vendors and payments.", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
