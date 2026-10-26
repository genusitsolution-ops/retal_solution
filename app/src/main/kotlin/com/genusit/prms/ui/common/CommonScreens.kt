package com.genusit.prms.ui.common

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background

import com.genusit.prms.platform.AppLanguage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.BuildConfig
import com.genusit.prms.R
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun ReceiptScreen(receiptNo: String, onBack: () -> Unit) {
    val vm = hiltViewModel<ReceiptViewModel>().receipt
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val chooser = stringResource(R.string.share_receipt)
    ScreenScaffold(stringResource(R.string.receipt), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { r ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletCard(stringResource(R.string.amount_paid), r.amount, r.modeLabel + (r.referenceNumber?.let { " · $it" } ?: ""))
                AppCard {
                    Column {
                        Text(r.ownerName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.rent_receipt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LabelValue(stringResource(R.string.receipt_no), r.receiptNumber, bold = true)
                        LabelValue(stringResource(R.string.date), dateText(r.date))
                        LabelValue(stringResource(R.string.received_from), r.tenantName)
                        LabelValue(stringResource(R.string.property), listOfNotNull(r.propertyCode, r.city).joinToString(", "))
                        LabelValue(stringResource(R.string.collected_by), r.collectedBy)
                        if (!r.remarks.isNullOrBlank()) LabelValue(stringResource(R.string.remarks), r.remarks!!)
                    }
                }
                r.lines.forEach { ln ->
                    AppCard {
                        Column {
                            Text(ln.periodLabel + " · " + ln.invoiceNumber, fontWeight = FontWeight.SemiBold)
                            LabelValue(stringResource(R.string.invoice_total), Money.format(ln.invoiceTotal))
                            LabelValue(stringResource(R.string.this_payment), Money.format(ln.applied), Palette.Success)
                            LabelValue(stringResource(R.string.balance_now), Money.format(ln.balanceAfter), if (ln.balanceAfter > 0) Palette.Danger else Palette.Success, bold = true)
                        }
                    }
                }
                Button(
                    // message built on the phone in the app's language (not the server's pre-built text)
                    onClick = { ReceiptShare.share(ctx, r) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (com.genusit.prms.domain.util.ReceiptMessage.normalizeIndianMobile(r.tenantPhone) != null) R.string.send_whatsapp else R.string.share_receipt)) }
            }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val model: NotificationsViewModel = hiltViewModel()
    val vm = model.notifications
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.notifications), onBack = onBack, actions = {
        IconButton(onClick = model::markAllRead) {
            Icon(Icons.Filled.DoneAll, stringResource(R.string.mark_all_read))
        }
    }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.items.isEmpty()) item { EmptyState(stringResource(R.string.no_notifications), Icons.Filled.Notifications) }
                items(d.items, key = { it.id }) { n ->
                    AppCard {
                        Row {
                            if (!n.isRead) Text("● ", color = Palette.Brand)
                            Column(Modifier.weight(1f)) {
                                Text(n.title, fontWeight = if (n.isRead) FontWeight.Medium else FontWeight.Bold)
                                Text(n.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dateText(n.createdAt), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(role: Role, onBack: (() -> Unit)?) {
    val model: SettingsViewModel = hiltViewModel()
    val lang = AppLanguage.current()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val session by model.session.collectAsStateWithLifecycle()

    val brand by hiltViewModel<com.genusit.prms.ui.auth.BrandingViewModel>().branding.collectAsStateWithLifecycle()

    ScreenScaffold(stringResource(R.string.settings), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // published app branding (logo + name), same as the login screen
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.HeaderGradient).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                com.genusit.prms.ui.auth.BrandLogo(brand.logo, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(brand.name ?: stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (brand.showTagline) Text(brand.tagline ?: stringResource(R.string.login_tagline), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                }
            }
            AppCard {
                Column {
                    Text(session?.name ?: "", style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(when (role) { Role.OWNER -> R.string.role_owner; Role.EMPLOYEE -> R.string.role_employee; else -> R.string.role_tenant }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Language, null, tint = Palette.Brand); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English", "hi" to "हिंदी", "mr" to "मराठी").forEach { (code, label) ->
                            FilterChip(selected = lang == code, onClick = {
                                // saved on the server too, so web, receipts and notifications follow the same language
                                model.setLanguage(code)
                            }, label = { Text(label) })
                        }
                    }
                }
            }
            ProfileCard(model, role)
            ChangePasswordCard(model, role)
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Palette.Danger); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.logout), color = Palette.Danger)
            }
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(stringResource(R.string.logout)) },
        text = { Text(stringResource(R.string.logout_confirm)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; model.logout() }) { Text(stringResource(R.string.logout)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Phone and e-mail of the signed-in user (owner via auth/me.php, tenant/employee via their profile endpoint). */
@Composable
private fun ProfileCard(model: SettingsViewModel, role: Role) {
    val state by model.me.state.collectAsStateWithLifecycle()
    val act by model.profileAction.state.collectAsStateWithLifecycle()
    val me = (state as? UiState.Data)?.value ?: return
    var phone by rememberSaveable(me.phone) { mutableStateOf(me.phone.orEmpty()) }
    var email by rememberSaveable(me.email) { mutableStateOf(me.email.orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val phoneOk = com.genusit.prms.domain.util.Validators.phone(phone)
    val emailOk = (role != Role.OWNER && email.isEmpty()) || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.my_details), style = MaterialTheme.typography.titleMedium)
            me.username?.let { LabelValue(stringResource(R.string.username), it) }
            FormField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, stringResource(R.string.phone), tried && !phoneOk, KeyboardType.Phone)
            FormField(email, { email = it.trim().take(150) }, stringResource(R.string.email), tried && !emailOk, KeyboardType.Email)
            ActionError(act)
            if (act is ActionState.Done<*>) SuccessText(stringResource(R.string.saved))
            Button(onClick = {
                tried = true
                if (phoneOk && emailOk) model.saveContact(role, phone, email.ifBlank { null })
            }, enabled = act !is ActionState.Running) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun ChangePasswordCard(model: SettingsViewModel, role: Role) {
    val act by model.passwordAction.state.collectAsStateWithLifecycle()
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var localErr by rememberSaveable { mutableStateOf(0) }
    EventEffect(model) { if (it == ScreenEvent.PasswordChanged) { current = ""; next = ""; confirm = "" } }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.change_password), style = MaterialTheme.typography.titleMedium)
            listOf(
                Triple(current, R.string.current_password) { v: String -> current = v },
                Triple(next, R.string.new_password) { v: String -> next = v },
                Triple(confirm, R.string.confirm_password) { v: String -> confirm = v },
            ).forEach { (value, label, set) ->
                OutlinedTextField(value, set, label = { Text(stringResource(label)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
            }
            if (localErr != 0) Text(stringResource(localErr), color = MaterialTheme.colorScheme.error)
            (act as? ActionState.Failed)?.let { Text(errorText(it.error), color = MaterialTheme.colorScheme.error) }
            if (act is ActionState.Done<*>) Text(stringResource(R.string.password_changed), color = Palette.Success)
            Button(onClick = {
                localErr = when {
                    current.isEmpty() -> R.string.err_current_password
                    next.length < 6 -> R.string.err_password_short
                    next != confirm -> R.string.err_password_mismatch
                    else -> 0
                }
                if (localErr == 0) model.changePassword(role, current, next)
            }, enabled = act !is ActionState.Running) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.save))
            }
        }
    }
}
