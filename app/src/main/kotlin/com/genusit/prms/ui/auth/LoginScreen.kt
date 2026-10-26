package com.genusit.prms.ui.auth

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.platform.AppLanguage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.genusit.prms.BuildConfig
import com.genusit.prms.R
import com.genusit.prms.domain.model.Role
import com.genusit.prms.ui.common.UiError
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val model: LoginViewModel = hiltViewModel()
    val ui by model.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    val busy = ui.busy
    val error = ui.error
    val errorRes = ui.errorRes
    // Debug builds only: which server was called and the technical reason it failed.
    val debugInfo = ui.debugInfo
    val lang = AppLanguage.current()
    // name, tagline and logo published by the Super Admin (cached; built-in look when absent or offline)
    val brandingModel: BrandingViewModel = hiltViewModel()
    val brand by brandingModel.branding.collectAsStateWithLifecycle()
    var signingUp by rememberSaveable { mutableStateOf(false) }

    fun submit() = model.submit(username, password, lang)

    if (signingUp) {
        SignupScreen(brand, onBack = { signingUp = false })
        return
    }

    Box(Modifier.fillMaxSize().background(Palette.HeaderGradient)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                listOf("en" to "EN", "hi" to "हिं", "mr" to "मरा").forEach { (code, label) ->
                    FilterChip(
                        selected = lang == code,
                        onClick = { AppLanguage.set(code) },
                        label = { Text(label, color = if (lang == code) Palette.BrandDark else Color.White) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            BrandLogo(brand.loginLogo, 72.dp)
            Spacer(Modifier.height(14.dp))
            Text(brand.name ?: stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (brand.showTagline) Text(brand.tagline ?: stringResource(R.string.login_tagline), color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.login_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username_or_email)) },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, stringResource(R.string.show_password))
                            }
                        },
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val msg = error ?: if (errorRes != 0) stringResource(errorRes) else null
                    if (msg != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    debugInfo?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.sign_in), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.login_roles_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            // shown unless the Super Admin turned self-registration off (unknown → shown; the server decides)
            if (brand.signupEnabled != false) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedButton(onClick = { signingUp = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .7f))) {
                    Text(stringResource(R.string.new_owner_signup), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
