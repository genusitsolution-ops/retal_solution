package com.genusit.prms.ui.auth

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
import com.genusit.prms.core.model.Role
import com.genusit.prms.core.net.ApiException
import com.genusit.prms.data.AppContainer
import com.genusit.prms.data.Session
import com.genusit.prms.ui.common.LocalAppContainer
import com.genusit.prms.ui.common.UiError
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var errorRes by rememberSaveable { mutableStateOf(0) }
    // Debug builds only: which server was called and the technical reason it failed.
    var debugInfo by rememberSaveable { mutableStateOf<String?>(null) }
    val lang = AppContainer.currentLanguage()

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isEmpty()) { errorRes = R.string.login_required; error = null; return }
        busy = true; error = null; errorRes = 0; debugInfo = null
        scope.launch {
            try {
                val r = container.api.login(username.trim(), password, lang)
                val role = Role.from(r.userType)
                if (role == null || role == Role.ADMIN) {
                    // Super Admin works in the web panel only
                    runCatching { container.api.logout() }
                    errorRes = R.string.login_admin_web
                } else {
                    container.session.save(Session(r.token, role, r.user.fullName))
                }
            } catch (e: ApiException) {
                when {
                    e.isNetwork -> errorRes = R.string.error_network
                    // Never show raw "HTTP 301" / HTML to users; details go to debugInfo (debug builds).
                    e.isServerMisconfig -> errorRes = R.string.error_server
                    else -> error = e.message
                }
                if (BuildConfig.DEBUG && (e.isNetwork || e.isServerMisconfig)) {
                    debugInfo = "Server: ${BuildConfig.API_BASE_URL}\n${e.message}"
                }
            } catch (e: Exception) {
                errorRes = R.string.error_generic
            } finally {
                busy = false
            }
        }
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
                        onClick = { AppContainer.setLanguage(code) },
                        label = { Text(label, color = if (lang == code) Palette.BrandDark else Color.White) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Apartment, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.login_tagline), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
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
        }
    }
}
