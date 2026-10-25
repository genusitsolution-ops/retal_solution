package com.genusit.prms.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.theme.Palette

/** One labelled text field used by every form (red outline after the first submit attempt). */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    enabled: Boolean = true,
    supporting: String? = null,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
    autoCorrect: Boolean = true,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        enabled = enabled,
        supportingText = supporting?.let { s -> { Text(s) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrect && !password,
            imeAction = imeAction,
        ),
        // explicit colours: typed text, cursor and selection stay readable on every device theme
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.Ink, unfocusedTextColor = Palette.Ink, disabledTextColor = Palette.Muted, errorTextColor = Palette.Ink,
            focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card, disabledContainerColor = Color(0xFFF8FAFC), errorContainerColor = Palette.Card,
            cursorColor = Palette.Brand, errorCursorColor = Palette.Danger,
            focusedBorderColor = Palette.Brand, unfocusedBorderColor = Color(0xFFCBD5E1),
            focusedLabelColor = Palette.Brand, unfocusedLabelColor = Palette.Muted,
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(handleColor = Palette.Brand, backgroundColor = Palette.Brand.copy(alpha = .25f)),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Keeps only characters that can form an amount ("1234.50"). */
fun amountInput(s: String): String = s.filter { it.isDigit() || it == '.' }.take(12)

/** Primary submit button that shows a spinner while [state] is running. */
@Composable
fun SubmitButton(label: String, state: ActionState<*>, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = state !is ActionState.Running, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (state is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(label, fontWeight = FontWeight.Bold)
    }
}

/** Server error (already localized) for a failed action, nothing otherwise. */
@Composable
fun ActionError(state: ActionState<*>) {
    (state as? ActionState.Failed)?.let { Text(errorText(it.error), color = MaterialTheme.colorScheme.error) }
}

/** "Are you sure?" dialog for every destructive action (delete, cancel, close, deactivate). */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (danger) Palette.Danger else Palette.Brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Dialog asking for one amount, never more than [max] (the server checks again). */
@Composable
fun AmountDialog(title: String, max: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val v = Money.parse(text)
    val ok = v != null && v > 0 && v <= max + 0.005
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.max_collectable) + ": " + Money.format(max), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(text, { text = amountInput(it) }, stringResource(R.string.amount), tried && !ok, KeyboardType.Decimal)
            }
        },
        confirmButton = { TextButton(onClick = { tried = true; if (ok) onConfirm(v!!) }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Dialog asking for a short text (reason, notes, reply). [required] blocks an empty value. */
@Composable
fun TextDialog(title: String, label: String, confirmLabel: String, required: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit, message: String? = null) {
    var text by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(text, { text = it.take(1000) }, label, tried && required && text.isBlank(), singleLine = false, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { tried = true; if (!required || text.isNotBlank()) onConfirm(text.trim()) }) { Text(confirmLabel, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Green confirmation line after a successful action. */
@Composable
fun SuccessText(text: String) {
    Text(text, color = Palette.Success, fontWeight = FontWeight.SemiBold)
}

/**
 * Read-only field that opens a list dialog to pick one option (rooms, tenants, months...).
 * A plain dialog is used instead of ExposedDropdownMenuBox so it behaves the same on every
 * Material 3 version and with long lists.
 */
@Composable
fun <T> ChoiceField(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, isError: Boolean = false, emptyText: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Box {
        OutlinedTextField(
            value = current, onValueChange = {}, readOnly = true, label = { Text(label) }, isError = isError,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        // transparent layer on top so a tap anywhere on the field opens the list
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = {
            if (options.isEmpty()) Text(emptyText ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { (value, text) ->
                    Text(
                        text,
                        fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (value == selected) Palette.Brand else Color.Unspecified,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value); open = false }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
