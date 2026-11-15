package com.genusit.prms.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.ChargeRateUpdate
import com.genusit.prms.domain.usecase.MeterCalc
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState

/**
 * "Change rate" for an existing per-unit charge in Charge Master: one rate per unit field.
 * Only bills generated afterwards use the new rate; bills already made keep theirs.
 */
@Composable
fun ChangeRateDialog(c: Charge, act: ActionState<*>, onDismiss: () -> Unit, onSave: (ChargeRateUpdate) -> Unit) {
    var rate by remember { mutableStateOf(c.ratePerUnit?.let { MeterCalc.num(it) } ?: "") }
    var tried by remember { mutableStateOf(false) }
    val r = Money.parse(rate)
    val bad = r == null || r < 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rate, { rate = com.genusit.prms.ui.common.amountInput(it) }, label = { Text(stringResource(R.string.rate_per_unit)) },
                    singleLine = true, isError = tried && bad, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp))
                if (tried && bad) Text(stringResource(R.string.rate_per_unit_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.rate_change_future_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionError(act)
            }
        },
        confirmButton = {
            TextButton(onClick = { tried = true; if (!bad) onSave(ChargeRateUpdate(r!!)) }, enabled = act !is ActionState.Running) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
