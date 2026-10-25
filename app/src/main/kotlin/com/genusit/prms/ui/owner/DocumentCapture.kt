package com.genusit.prms.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.util.DocumentType
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.theme.Palette
import java.io.File

/** What the review dialog copies into the tenant form. Null = leave that field unchanged. */
data class DocumentApply(val name: String?, val number: String?, val address: String?)

/**
 * "Read details from an ID document" card for the tenant form: consent first, then camera or
 * gallery; [onImage] receives the photo Uri (and the raw camera file to delete after reading).
 */
@Composable
fun DocumentCaptureCard(
    reading: Boolean,
    newCaptureTarget: () -> Pair<File, Uri>,
    onImage: (uri: Uri, type: DocumentType, captureFile: File?) -> Unit,
    uploads: Map<DocumentType, DocUpload> = emptyMap(),
    onRetry: (DocumentType) -> Unit = {},
    onRemove: (DocumentType) -> Unit = {},
) {
    var consent by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(DocumentType.AADHAAR) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let(::File)
        val uri = pendingUri?.let(Uri::parse)
        if (ok && uri != null) onImage(uri, type, file) else file?.delete()
        pendingPath = null; pendingUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri, type, null)
    }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.DocumentScanner, Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.doc_capture_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.doc_capture_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(stringResource(R.string.doc_consent), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == DocumentType.AADHAAR, onClick = { type = DocumentType.AADHAAR }, label = { Text(stringResource(R.string.doc_aadhaar)) })
                FilterChip(selected = type == DocumentType.PAN, onClick = { type = DocumentType.PAN }, label = { Text(stringResource(R.string.doc_pan)) })
            }
            if (reading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Brand)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.doc_reading))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = consent, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {
                        val (file, uri) = newCaptureTarget()
                        pendingPath = file.absolutePath; pendingUri = uri.toString()
                        camera.launch(uri)
                    },
                ) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_camera)) }
                OutlinedButton(
                    enabled = consent, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_gallery)) }
            }
            // each captured document: preview + upload progress / success / retry
            listOf(DocumentType.AADHAAR, DocumentType.PAN).forEach { t ->
                uploads[t]?.let { up -> UploadRow(t, up, onRetry = { onRetry(t) }, onRemove = { onRemove(t) }) }
            }
        }
    }
}

@Composable
private fun UploadRow(type: DocumentType, up: DocUpload, onRetry: () -> Unit, onRemove: () -> Unit) {
    androidx.compose.animation.AnimatedContent(targetState = up, label = "upload") { state ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DocThumb(state.preview)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (type == DocumentType.PAN) R.string.doc_pan else R.string.doc_aadhaar), fontWeight = FontWeight.SemiBold)
                when (state) {
                    is DocUpload.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Brand)
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_uploading), style = MaterialTheme.typography.labelMedium)
                    }
                    is DocUpload.Uploaded -> Text(stringResource(R.string.doc_uploaded_draft), style = MaterialTheme.typography.labelMedium, color = Palette.Success)
                    is DocUpload.Failed -> Text(com.genusit.prms.ui.common.errorText(state.error), style = MaterialTheme.typography.labelMedium, color = Palette.Danger)
                }
            }
            if (state is DocUpload.Failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onRemove, enabled = state !is DocUpload.Uploading) { Text(stringResource(R.string.remove), color = Palette.Danger) }
        }
    }
}

/** Small preview of the compressed photo (decoded off the main thread, down-sampled). */
@Composable
private fun DocThumb(file: File) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()
        }
    }
    val m = Modifier.size(width = 64.dp, height = 44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = m)
    else androidx.compose.foundation.layout.Box(m)
}

/**
 * Review step: every value read from the document is editable, and only ticked values are copied.
 * A value that would replace something already typed in the form starts unticked (never overwritten silently).
 */
@Composable
fun DocumentReviewDialog(
    type: DocumentType,
    name: String?, number: String?, address: String?,
    currentName: String, currentNumber: String, currentAddress: String,
    onApply: (DocumentApply) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Row3(val label: Int, val initial: String?, val current: String)
    val rows = listOf(
        Row3(R.string.full_name, name, currentName),
        Row3(if (type == DocumentType.AADHAAR) R.string.doc_number_aadhaar else R.string.doc_number_pan, number, currentNumber),
        Row3(R.string.address, address, currentAddress),
    )
    val values = remember { rows.map { mutableStateOf(it.initial.orEmpty()) } }
    val ticks = remember { rows.map { r -> mutableStateOf(r.initial != null && (r.current.isBlank() || r.current == r.initial)) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_review_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.doc_review_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEachIndexed { i, r ->
                    if (r.initial == null) return@forEachIndexed
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = ticks[i].value, onCheckedChange = { ticks[i].value = it })
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = values[i].value, onValueChange = { values[i].value = it; ticks[i].value = true },
                                label = { Text(stringResource(r.label)) }, singleLine = i != 2, minLines = if (i == 2) 2 else 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (r.current.isNotBlank() && r.current != r.initial) {
                                Text(stringResource(R.string.doc_replaces, r.current), style = MaterialTheme.typography.labelMedium, color = Palette.Warning)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun pick(i: Int) = values[i].value.trim().takeIf { ticks[i].value && rows[i].initial != null && it.isNotEmpty() }
                onApply(DocumentApply(pick(0), pick(1), pick(2)))
            }) { Text(stringResource(R.string.doc_apply), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
