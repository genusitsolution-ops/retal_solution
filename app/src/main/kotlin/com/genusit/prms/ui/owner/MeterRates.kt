package com.genusit.prms.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.ChargeRateUpdate
import com.genusit.prms.domain.model.Slab
import com.genusit.prms.domain.usecase.MeterCalc
import com.genusit.prms.domain.usecase.SlabProblem
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.theme.Palette

/**
 * 1.15 — how a per-unit charge (electricity, water) is worked out. Kept to plain choices:
 *   "One rate for all units" (rate per unit) · "Same rate for all units (by slab)" · "Each slab at its own rate".
 * Slabs are typed as "Units up to" + "Rate"; the last row is "Above that". A worked example under the table lets the
 * owner check the rates without any calculation. The server validates everything again.
 */
class SlabInput(upto: String, rate: String) {
    var upto by mutableStateOf(upto)
    var rate by mutableStateOf(rate)
}

fun slabRowsOf(c: Charge?): SnapshotStateList<SlabInput> = mutableStateListOf<SlabInput>().apply {
    val s = c?.slabs.orEmpty()
    if (s.size >= 2) s.forEach { add(SlabInput(it.upto?.let { u -> MeterCalc.num(u) } ?: "", MeterCalc.num(it.rate))) }
    else { add(SlabInput("10", "")); add(SlabInput("30", "")); add(SlabInput("", "")) }
}

private fun toSlabs(rows: List<SlabInput>): List<Slab>? = rows.mapIndexed { i, r ->
    val rate = Money.parse(r.rate) ?: return null
    val upto = if (i == rows.lastIndex) null else (Money.parse(r.upto) ?: return null)
    Slab(upto, rate)
}

/** The rule to save, or the message why it cannot be saved yet. */
fun rateRule(mode: String, rateText: String, rows: List<SlabInput>): Pair<ChargeRateUpdate?, Int?> {
    if (mode == MeterCalc.FLAT) {
        val r = Money.parse(rateText) ?: return null to R.string.rate_per_unit_required
        if (r < 0) return null to R.string.rate_per_unit_required
        return ChargeRateUpdate(r, MeterCalc.FLAT) to null
    }
    val slabs = toSlabs(rows) ?: return null to R.string.slab_fill_all
    return when (MeterCalc.slabProblem(slabs)) {
        null -> ChargeRateUpdate(Money.parse(rateText) ?: slabs.first().rate, mode, slabs) to null
        SlabProblem.TooFew -> null to R.string.slab_too_few
        SlabProblem.TooMany -> null to R.string.slab_too_many
        SlabProblem.BadRate -> null to R.string.slab_fill_all
        SlabProblem.MissingUnits, SlabProblem.LastHasUnits -> null to R.string.slab_fill_all
        SlabProblem.NotIncreasing -> null to R.string.slab_increasing
    }
}

@Composable
fun rateModeLabel(mode: String): String = stringResource(
    when (mode) { MeterCalc.SINGLE -> R.string.rate_mode_single; MeterCalc.PROGRESSIVE -> R.string.rate_mode_progressive; else -> R.string.rate_mode_flat },
)

/** Rate rule editor used when adding a per-unit charge and when changing its rates. */
@Composable
fun RateRuleEditor(mode: String, onMode: (String) -> Unit, rateText: String, onRate: (String) -> Unit, rows: SnapshotStateList<SlabInput>, tried: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.rate_mode_question), style = MaterialTheme.typography.titleSmall)
        MeterCalc.MODES.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (mode == m) Palette.BrandSoft else Palette.Card)
                    .selectable(selected = mode == m, role = Role.RadioButton, onClick = { onMode(m) }).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = null, modifier = Modifier.padding(8.dp))
                Text(rateModeLabel(m), style = MaterialTheme.typography.bodyMedium, fontWeight = if (mode == m) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (mode == MeterCalc.FLAT) {
            OutlinedTextField(rateText, { onRate(com.genusit.prms.ui.common.amountInput(it)) }, label = { Text(stringResource(R.string.rate_per_unit)) },
                singleLine = true, isError = tried && Money.parse(rateText) == null, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp))
            return@Column
        }
        Text(stringResource(if (mode == MeterCalc.SINGLE) R.string.slab_help_single else R.string.slab_help_progressive),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed { i, r ->
            val last = i == rows.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (last) Text(stringResource(R.string.slab_above_that), Modifier.weight(1f).padding(start = 4.dp), fontWeight = FontWeight.SemiBold)
                else OutlinedTextField(r.upto, { r.upto = com.genusit.prms.ui.common.amountInput(it) }, label = { Text(stringResource(R.string.slab_units_up_to)) },
                    singleLine = true, modifier = Modifier.weight(1f), isError = tried && Money.parse(r.upto) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(r.rate, { r.rate = com.genusit.prms.ui.common.amountInput(it) }, label = { Text(stringResource(R.string.rate_rs)) },
                    singleLine = true, modifier = Modifier.weight(1f), isError = tried && Money.parse(r.rate) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp))
                if (!last && rows.size > 2) IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                else Spacer(Modifier.width(48.dp))
            }
        }
        if (rows.size < 8) TextButton(onClick = { rows.add(rows.lastIndex, SlabInput("", "")) }) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.slab_add))
        }
        // worked example so the owner can check the slabs (same maths as the bill)
        val slabs = toSlabs(rows)
        val unitsWord = stringResource(R.string.units)
        val example = if (slabs != null && MeterCalc.slabProblem(slabs) == null) listOf(10.0, 20.0, 60.0).joinToString(" · ") { u ->
            "${MeterCalc.num(u)} $unitsWord = ${Money.format(MeterCalc.calc(u, mode, slabs, 0.0).amount)}"
        } else null
        Text(if (example != null) stringResource(R.string.slab_check_x, example) else stringResource(R.string.slab_fill_all),
            style = MaterialTheme.typography.labelMedium, color = if (example != null) Palette.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.TealSoft.copy(alpha = .5f)).padding(10.dp))
    }
}

/** "Change rate" for an existing per-unit charge. Only bills generated afterwards use the new rate. */
@Composable
fun ChangeRateDialog(c: Charge, act: ActionState<*>, onDismiss: () -> Unit, onSave: (ChargeRateUpdate) -> Unit) {
    var mode by remember { mutableStateOf(MeterCalc.effectiveMode(c.rateMode, c.slabs)) }
    var rate by remember { mutableStateOf(c.ratePerUnit?.let { MeterCalc.num(it) } ?: "") }
    val rows = remember { slabRowsOf(c) }
    var tried by remember { mutableStateOf(false) }
    val (rule, problem) = rateRule(mode, rate, rows)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RateRuleEditor(mode, { mode = it }, rate, { rate = it }, rows, tried)
                Text(stringResource(R.string.rate_change_future_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tried && problem != null) Text(stringResource(problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ActionError(act)
            }
        },
        confirmButton = {
            TextButton(onClick = { tried = true; if (rule != null) onSave(rule) }, enabled = act !is ActionState.Running) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
