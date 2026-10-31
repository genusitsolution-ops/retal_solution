package com.genusit.prms.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.genusit.prms.R
import com.genusit.prms.domain.model.RegisterInput
import com.genusit.prms.domain.model.RegisterResult
import com.genusit.prms.domain.usecase.RegisterOwnerUseCase
import com.genusit.prms.domain.usecase.RegisterOwnerUseCase.Problem
import com.genusit.prms.platform.Branding
import com.genusit.prms.platform.BrandingStore
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Branding for the login / sign-up screens; refreshed from the server every time the login screen opens. */
@HiltViewModel
class BrandingViewModel @Inject constructor(private val store: BrandingStore) : ViewModel() {
    val branding: StateFlow<Branding> = store.branding
    init { viewModelScope.launch { store.refresh() } }
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val register: RegisterOwnerUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val action = action<RegisterResult>()
    fun check(i: RegisterInput, confirm: String): Set<Problem> = register.check(i, confirm)
    fun submit(i: RegisterInput) = action.run { register(i) }
}

/** The published logo (cached file), or the built-in house icon. */
@Composable
fun BrandLogo(file: File?, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file) {
        value = file?.let { f -> withContext(Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() } }
    }
    val b = bmp
    Box(modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(if (b != null) Color.White else Color.White.copy(alpha = .15f)),
        contentAlignment = Alignment.Center) {
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(size / 9).fillMaxSize())
        else Icon(Icons.Filled.Apartment, null, tint = Color.White, modifier = Modifier.size(size * 0.53f))
    }
}

@Composable
fun SignupScreen(branding: Branding, onBack: () -> Unit) {
    val model: SignupViewModel = hiltViewModel()
    val act by model.action.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val input = RegisterInput(name, phone, email, username, password, code.ifBlank { null })
    val problems = model.check(input, confirm)
    fun bad(p: Problem) = tried && p in problems

    Box(Modifier.fillMaxSize().background(Palette.HeaderGradient)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Spacer(Modifier.weight(1f))
            }
            BrandLogo(branding.loginLogo, 60.dp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.signup_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.signup_subtitle, branding.name ?: stringResource(R.string.app_name)), color = Color.White.copy(alpha = .85f),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                val done = (act as? ActionState.Done)?.result
                if (done != null) SignupResult(done, onBack)
                else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // the free trial the new owner gets immediately (configured by the Super Admin)
                    branding.trial?.let { t ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.BrandSoft).padding(12.dp)) {
                            Text(stringResource(R.string.signup_trial_title, t.packageName, t.days), fontWeight = FontWeight.Bold, color = Palette.Brand)
                            Text(stringResource(R.string.signup_trial_sub, t.maxProperties, t.maxTenants), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FormField(name, { name = it.take(150) }, stringResource(R.string.full_name), bad(Problem.NAME), capitalization = KeyboardCapitalization.Words)
                    FormField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, stringResource(R.string.phone), bad(Problem.PHONE), KeyboardType.Phone)
                    FormField(email, { email = it.trim().take(150) }, stringResource(R.string.email), bad(Problem.EMAIL), KeyboardType.Email, autoCorrect = false)
                    FormField(username, { username = it.trim().take(80) }, stringResource(R.string.username), bad(Problem.USERNAME), autoCorrect = false,
                        supporting = stringResource(R.string.signup_username_hint))
                    FormField(password, { password = it.take(100) }, stringResource(R.string.password), bad(Problem.PASSWORD), password = true,
                        supporting = stringResource(R.string.signup_password_hint))
                    FormField(confirm, { confirm = it.take(100) }, stringResource(R.string.confirm_password), bad(Problem.PASSWORD_MISMATCH), password = true,
                        supporting = if (bad(Problem.PASSWORD_MISMATCH)) stringResource(R.string.passwords_differ) else null)
                    if (branding.referralEnabled) {
                        FormField(code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(16) }, stringResource(R.string.referral_code_optional),
                            keyboard = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrect = false,
                            supporting = stringResource(R.string.referral_code_hint))
                    }
                    ActionError(act)
                    Button(onClick = { tried = true; if (problems.isEmpty()) model.submit(input) }, enabled = act !is ActionState.Running,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        if (act is ActionState.Running) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.create_account), fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.signup_owner_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SignupResult(r: RegisterResult, onSignIn: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (r.isActive) Icons.Filled.CheckCircle else Icons.Filled.HourglassTop, null, tint = if (r.isActive) Palette.Success else Palette.Warning, modifier = Modifier.size(56.dp))
        Text(stringResource(if (r.isActive) R.string.signup_done_active else R.string.signup_done_pending), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if (r.isActive) R.string.signup_done_active_sub else R.string.signup_done_pending_sub),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        r.trial?.let { t ->
            Card(colors = CardDefaults.cardColors(containerColor = Palette.BrandSoft), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.trial_active), fontWeight = FontWeight.Bold, color = Palette.BrandDark)
                    t.packageName?.let { Text(it, color = Palette.BrandDark) }
                    Text(dateText(t.startDate) + " – " + dateText(t.endDate), color = Palette.BrandDark)
                }
            }
        }
        when (r.referral) {
            "recorded" -> Text(stringResource(R.string.signup_referral_recorded), color = Palette.Success, style = MaterialTheme.typography.labelMedium)
            "not_recorded" -> Text(stringResource(R.string.signup_referral_not_recorded), color = Palette.Warning, style = MaterialTheme.typography.labelMedium)
        }
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.back_to_sign_in), fontWeight = FontWeight.Bold)
        }
    }
}
