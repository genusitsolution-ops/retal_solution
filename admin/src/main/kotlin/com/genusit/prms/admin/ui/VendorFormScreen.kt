package com.genusit.prms.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.ApiException
import com.genusit.prms.admin.net.CreatedVendor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Checks the same rules as the server (Vendor::cleanIndependent) so mistakes show next to the field before saving. */
internal fun vendorFormErrors(v: Map<String, String>, creating: Boolean): Map<String, String> = buildMap {
    fun s(k: String) = v[k].orEmpty().trim()
    if (s("full_name").isEmpty()) put("full_name", "Enter the vendor's full name.")
    if (!Regex("^[6-9]\\d{9}$").matches(s("mobile"))) put("mobile", "Enter a valid 10-digit mobile number.")
    if (s("whatsapp").isNotEmpty() && !Regex("^[6-9]\\d{9}$").matches(s("whatsapp"))) put("whatsapp", "Enter a valid 10-digit WhatsApp number.")
    if (s("email").isNotEmpty() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s("email"))) put("email", "Enter a valid email address.")
    if (s("address").isEmpty()) put("address", "Enter the address.")
    if (s("city").isEmpty()) put("city", "Enter the city.")
    if (s("state").isEmpty()) put("state", "Enter the state.")
    if (!Regex("^\\d{6}$").matches(s("pincode"))) put("pincode", "Enter a valid 6-digit PIN code.")
    val p = s("commission_percent").removeSuffix("%").trim().toDoubleOrNull()
    if (p == null || p <= 0 || p > 100) put("commission_percent", "Enter the commission percentage (0.01 to 100).")
    if (s("upi_id").isNotEmpty() && !Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$").matches(s("upi_id"))) put("upi_id", "Enter a valid UPI ID (for example name@bank).")
    if (s("ifsc").isNotEmpty() && !Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(s("ifsc").uppercase())) put("ifsc", "Enter a valid 11-character IFSC code.")
    if (s("account_number").isNotEmpty() && !Regex("^\\d{6,20}$").matches(s("account_number").replace(" ", ""))) put("account_number", "Enter a valid bank account number (digits only).")
    if (s("payout_method") == "bank") {
        if (s("account_holder").isEmpty()) put("account_holder", "Needed for bank transfer.")
        if (s("account_number").isEmpty()) putIfAbsent("account_number", "Needed for bank transfer.")
        if (s("ifsc").isEmpty()) putIfAbsent("ifsc", "Needed for bank transfer.")
    }
    if (creating && s("password").isNotEmpty() && s("password").length < 6) put("password", "At least 6 characters, or leave empty to generate one.")
    if (creating && s("registered_at").isNotEmpty() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s("registered_at"))) put("registered_at", "Use YYYY-MM-DD.")
}

/**
 * Add a new independent vendor (own sign-in, VEN… code) or edit one. Uses the server's create_independent /
 * update_independent actions; the server checks everything again.
 */
@Composable
fun VendorFormScreen(nav: NavHostController, id: Int?) {
    val api = appApi(); val session = appSession()
    val creating = id == null
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf("payout_method" to "upi", "status" to "active", "registered_at" to today(), "vendor_can_edit_payment" to "") }
    var loaded by remember { mutableStateOf(creating) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<CreatedVendor?>(null) }
    val errors = vendorFormErrors(values, creating)

    if (!creating) LaunchedEffect(id) {
        if (values["full_name"] != null) return@LaunchedEffect
        try {
            val d = api.vendor(id!!)
            val p = d.profile ?: throw ApiException("NOT_INDEPENDENT", "Only vendors registered here (not owner accounts) can be edited on this screen.")
            values.putAll(mapOf("full_name" to p.fullName, "mobile" to p.mobile.orEmpty(), "whatsapp" to p.whatsapp.orEmpty(), "email" to p.email.orEmpty(),
                "address" to p.address.orEmpty(), "city" to p.city.orEmpty(), "state" to p.state.orEmpty(), "pincode" to p.pincode.orEmpty(),
                "commission_percent" to plain(p.commissionPercent), "payout_method" to (p.payment.payoutMethod ?: "upi"), "upi_id" to p.payment.upiId.orEmpty(),
                "account_holder" to p.payment.accountHolder.orEmpty(), "bank_name" to p.payment.bankName.orEmpty(), "account_number" to p.accountNumber.orEmpty(),
                "ifsc" to p.payment.ifsc.orEmpty(), "vendor_can_edit_payment" to if (p.payment.canEdit) "1" else "", "remarks" to d.vendor.remarks.orEmpty()))
            loaded = true
        } catch (e: CancellationException) { throw e } catch (e: ApiException) {
            if (e.isSessionEnded) session.clear(); loadError = e.message
        } catch (e: Exception) { loadError = e.message ?: "Could not load the vendor." }
    }

    created?.let { c ->
        CredentialsDialog("Vendor registered", values["full_name"].orEmpty(), c.vendorCode, c.mobile, values["whatsapp"], c.password) {
            created = null
            nav.navigate("vendor/${c.id}") { popUpTo("vendor/new") { inclusive = true } }
        }
    }

    fun submit() {
        tried = true; serverError = null
        if (errors.isNotEmpty() || busy) return
        busy = true
        val keys = mutableListOf("full_name", "mobile", "whatsapp", "email", "address", "city", "state", "pincode", "commission_percent",
            "payout_method", "upi_id", "account_holder", "bank_name", "account_number", "ifsc", "vendor_can_edit_payment", "remarks")
        if (creating) keys += listOf("status", "password", "registered_at")
        val body = keys.associateWith { k -> values[k].orEmpty().trim().let { if (k == "ifsc") it.uppercase() else if (k == "account_number") it.replace(" ", "") else it } }
        scope.launch {
            try {
                if (creating) { created = api.createVendor(body).second }
                else { api.updateIndependentVendor(id!!, body); nav.popBackStack() }
            } catch (e: CancellationException) { throw e } catch (e: ApiException) {
                if (e.isSessionEnded) session.clear(); serverError = e.message
            } catch (e: Exception) { serverError = "Something went wrong. ${e.message ?: ""}".trim() } finally { busy = false }
        }
    }

    @Composable
    fun F(key: String, label: String, required: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, hint: String? = null,
          multiline: Boolean = false, caps: KeyboardCapitalization = KeyboardCapitalization.None, help: String? = null) {
        val err = if (tried) errors[key] else null
        OutlinedTextField(values[key].orEmpty(), { values[key] = it }, label = { Text(label + if (required) " *" else "") },
            placeholder = hint?.let { { Text(it) } }, isError = err != null, singleLine = !multiline, minLines = if (multiline) 2 else 1,
            supportingText = (err ?: help)?.let { { Text(it) } }, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = caps), modifier = Modifier.fillMaxWidth().testTag("vf_$key"))
    }

    Box {
    Page(if (creating) "Add vendor" else "Edit vendor", nav, subtitle = if (creating) "Independent vendor with own sign-in" else values["full_name"]) {
        if (!loaded) {
            item { if (loadError != null) AppCard { SmallText(loadError!!, C.Danger) } else LoadContent<Unit>(Load.Loading, {}) {} }
            return@Page
        }
        if (tried && errors.isNotEmpty()) item { Banner("Please correct the ${errors.size} highlighted field(s).") }
        serverError?.let { item { Banner(it) } }
        item {
            FormSection("Vendor details") {
                F("full_name", "Full name", required = true, caps = KeyboardCapitalization.Words)
                F("mobile", "Mobile number (login)", required = true, keyboard = KeyboardType.Phone, hint = "10 digits", help = "The vendor signs in with this number or the vendor code.")
                F("whatsapp", "WhatsApp number", keyboard = KeyboardType.Phone, help = "Leave empty if same as mobile")
                F("email", "Email", keyboard = KeyboardType.Email)
            }
        }
        item {
            FormSection("Address") {
                F("address", "Address", required = true, multiline = true, caps = KeyboardCapitalization.Sentences)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { F("city", "City", required = true, caps = KeyboardCapitalization.Words) }
                    Column(Modifier.weight(1f)) { F("pincode", "PIN code", required = true, keyboard = KeyboardType.Number) }
                }
                F("state", "State", required = true, caps = KeyboardCapitalization.Words)
            }
        }
        item {
            FormSection("Commission") {
                F("commission_percent", "Commission %", required = true, keyboard = KeyboardType.Decimal, hint = "e.g. 10",
                    help = "Earned on every confirmed renewal of an owner who registers with this vendor's code." + if (!creating) " A new % applies from now on." else "")
                if (creating) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = C.Muted)
                    Chips(listOf("active" to "Active", "inactive" to "Inactive"), values["status"] ?: "active") { values["status"] = it }
                    F("registered_at", "Registration date (YYYY-MM-DD)", keyboard = KeyboardType.Number)
                }
            }
        }
        item {
            FormSection("Payment details (for payouts)") {
                Text("Preferred payout method", style = MaterialTheme.typography.labelMedium, color = C.Muted)
                Chips(PayoutMethods, values["payout_method"] ?: "upi") { values["payout_method"] = it }
                F("upi_id", "UPI ID", hint = "name@bank", keyboard = KeyboardType.Email)
                F("account_holder", "Account holder name", required = values["payout_method"] == "bank", caps = KeyboardCapitalization.Words)
                F("bank_name", "Bank name", caps = KeyboardCapitalization.Words)
                F("account_number", "Account number", required = values["payout_method"] == "bank", keyboard = KeyboardType.Number)
                F("ifsc", "IFSC code", required = values["payout_method"] == "bank", caps = KeyboardCapitalization.Characters)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { values["vendor_can_edit_payment"] = if (values["vendor_can_edit_payment"] == "1") "" else "1" }
                    .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vendor can change these details", style = MaterialTheme.typography.bodyMedium)
                        SmallText("Off: only the Super Admin can change them.")
                    }
                    Switch(checked = values["vendor_can_edit_payment"] == "1", onCheckedChange = { values["vendor_can_edit_payment"] = if (it) "1" else "" })
                }
            }
        }
        item {
            FormSection(if (creating) "Sign-in" else "Notes") {
                if (creating) F("password", "Temporary password", keyboard = KeyboardType.Password,
                    help = "Leave empty to generate a secure one. The vendor must change it at the first sign-in.")
                F("remarks", "Remarks (internal)", multiline = true)
            }
        }
        item {
            Button(onClick = { submit() }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp).testTag("vf_save")) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text(if (creating) "Register vendor" else "Save changes")
            }
        }
    }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = C.BrandDark)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun Banner(text: String) =
    Text(text, color = C.Danger, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.DangerSoft).padding(12.dp))
