package com.genusit.prms.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.PhotoSubject
import com.genusit.prms.domain.usecase.GetProfilePhotoUseCase
import com.genusit.prms.domain.usecase.RemoveProfilePhotoUseCase
import com.genusit.prms.domain.usecase.UploadProfilePhotoUseCase
import com.genusit.prms.platform.DocumentScanner
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Profile photos of owners and tenants (app 1.8): one picker (camera / gallery → preview → confirm),
 * compression on the phone (max 1024 px, JPEG 85), authenticated upload, and a small in-memory cache
 * keyed by the server's photo version so a changed photo is fetched again. Separate from ID documents.
 */

/** Profile photo compression settings (good quality for a face, small upload). */
object PhotoSpec { const val MAX_SIDE = 1024; const val QUALITY = 85 }

/** Decoded photos by subject/id/version; a new version is a new key, so replaced photos are never shown stale. */
@Singleton
class PhotoCache @Inject constructor(private val getPhoto: GetProfilePhotoUseCase) {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = mutableSetOf<String>()

    suspend fun bitmap(subject: PhotoSubject, id: Int?, version: String?): Bitmap? {
        if (version == null) return null
        val key = "${subject.apiValue}:${id ?: 0}:$version"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        return try {
            val bytes = getPhoto(subject, id)
            withContext(Dispatchers.Default) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 256) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }?.also { cache.put(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(missing) { missing += key }
            null
        }
    }
}

/** Upload / remove a profile photo and load photos for display. */
@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val cache: PhotoCache,
    private val scanner: DocumentScanner,
    private val uploadPhoto: UploadProfilePhotoUseCase,
    private val removePhoto: RemoveProfilePhotoUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    /** Result: the new photo version (null after removing). */
    val action = action<String?>()

    fun newCaptureTarget(): Pair<File, Uri> = scanner.newCaptureTarget()

    suspend fun load(subject: PhotoSubject, id: Int?, version: String?): Bitmap? = cache.bitmap(subject, id, version)

    /** Compresses on the phone, uploads, and calls [onDone] with the new version once the server confirmed it. */
    fun upload(subject: PhotoSubject, id: Int?, source: Uri, consent: Boolean, captureFile: File?, onDone: (String?) -> Unit = {}) =
        action.run(onSuccess = onDone) {
            var compressed: File? = null
            try {
                compressed = scanner.compress(source, PhotoSpec.MAX_SIDE, PhotoSpec.QUALITY)
                uploadPhoto(subject, id, compressed, consent)
            } finally {
                scanner.discard(compressed); scanner.discard(captureFile)
            }
        }

    fun remove(subject: PhotoSubject, id: Int?, onDone: (String?) -> Unit = {}) = action.run(onSuccess = onDone) { removePhoto(subject, id); null }
}

/** Round photo, or the first letter of the name while there is none (or while it loads). */
@Composable
fun Avatar(subject: PhotoSubject, id: Int?, version: String?, name: String, size: Dp = 44.dp, preview: Bitmap? = null) {
    val photos: PhotoViewModel = hiltViewModel()
    val loaded by produceState<Bitmap?>(null, subject, id, version) { value = photos.load(subject, id, version) }
    AvatarImage(preview ?: loaded, name, size)
}

@Composable
fun AvatarImage(bitmap: Bitmap?, name: String, size: Dp) {
    val m = Modifier.size(size).clip(CircleShape)
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
    else Box(m.background(Palette.BrandSoft), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Palette.Brand, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/**
 * Camera / Gallery buttons → preview → "Use this photo". With [consentRequired] (a tenant's photo taken by
 * the owner) the buttons stay disabled until the owner confirms the tenant agreed.
 */
@Composable
fun PhotoPickerButtons(consentRequired: Boolean, enabled: Boolean = true, onPicked: (uri: Uri, captureFile: File?, consent: Boolean) -> Unit, newCaptureTarget: () -> Pair<File, Uri>) {
    var consent by rememberSaveable { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingUri != null) { previewUri = pendingUri; previewFile = pendingPath } else pendingPath?.let { File(it).delete() }
        pendingPath = null; pendingUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { previewUri = uri.toString(); previewFile = null }
    }
    val canPick = enabled && (!consentRequired || consent)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (consentRequired) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text(stringResource(R.string.photo_consent), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = canPick, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                val (file, uri) = newCaptureTarget()
                pendingPath = file.absolutePath; pendingUri = uri.toString()
                camera.launch(uri)
            }) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_camera)) }
            OutlinedButton(enabled = canPick, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_gallery)) }
        }
    }
    val pu = previewUri
    if (pu != null) {
        val ctx = LocalContext.current
        val bmp by produceState<Bitmap?>(null, pu) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(Uri.parse(pu))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                }.getOrNull()
            }
        }
        AlertDialog(
            onDismissRequest = { previewFile?.let { File(it).delete() }; previewUri = null; previewFile = null },
            title = { Text(stringResource(R.string.photo_preview_title)) },
            text = {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    else CircularProgressIndicator(color = Palette.Brand)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = previewFile?.let(::File)
                    previewUri = null; previewFile = null
                    onPicked(Uri.parse(pu), f, consent)
                }) { Text(stringResource(R.string.photo_use), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { previewFile?.let { File(it).delete() }; previewUri = null; previewFile = null }) { Text(stringResource(R.string.photo_retake)) } },
        )
    }
}

/**
 * Card with the photo and "change / remove": uploads right away (owner profile, tenant details).
 * [onChanged] gets the new version after the server confirmed it.
 */
@Composable
fun ProfilePhotoCard(subject: PhotoSubject, id: Int?, version: String?, name: String, consentRequired: Boolean, onChanged: (String?) -> Unit) {
    val photos: PhotoViewModel = hiltViewModel()
    val act by photos.action.state.collectAsStateWithLifecycle()
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(subject, id, version, name, 72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.photo_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.photo_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (act) {
                is ActionState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Brand)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.photo_uploading))
                }
                is ActionState.Done<*> -> SuccessText(stringResource(R.string.photo_saved))
                else -> ActionError(act)
            }
            PhotoPickerButtons(consentRequired, enabled = act !is ActionState.Running, newCaptureTarget = photos::newCaptureTarget,
                onPicked = { uri, file, consent -> photos.upload(subject, id, uri, consent, file, onChanged) })
            if (version != null) TextButton(onClick = { photos.remove(subject, id, onChanged) }, enabled = act !is ActionState.Running) {
                Text(stringResource(R.string.photo_remove), color = Palette.Danger)
            }
        }
    }
}
