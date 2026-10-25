package com.genusit.prms.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.genusit.prms.domain.util.DocumentFields
import com.genusit.prms.domain.util.DocumentParser
import com.genusit.prms.domain.util.DocumentType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Result of reading one document photo. [image] is the compressed copy kept for upload. */
data class ScanResult(val fields: DocumentFields, val image: File, val bytes: Long)

/**
 * Tenant ID document capture: compress + straighten the photo and read it with on-device OCR
 * (ML Kit, bundled model – the image never leaves the phone for OCR). Everything runs off the main thread.
 * Photos live only in the app's private cache and are deleted when no longer needed; nothing is logged.
 */
@Singleton
class DocumentScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.cacheDir, "documents").apply { mkdirs() }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** A private file + content Uri for the camera app to write one photo into. */
    fun newCaptureTarget(): Pair<File, Uri> {
        val f = File(dir, "capture-${UUID.randomUUID()}.jpg")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    suspend fun scan(source: Uri, type: DocumentType, maxSide: Int = MAX_SIDE, quality: Int = JPEG_QUALITY): ScanResult =
        withContext(Dispatchers.Default) {
            val bitmap = decodeUpright(source, maxSide) ?: throw IllegalArgumentException("unreadable image")
            val out = File(dir, "doc-${UUID.randomUUID()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            // a card photographed sideways: try the other orientations when nothing is found
            var fields = DocumentParser.parse(type, ocrLines(bitmap, 0))
            for (deg in intArrayOf(90, 270)) {
                if (!fields.isEmpty) break
                fields = DocumentParser.parse(type, ocrLines(bitmap, deg))
            }
            bitmap.recycle()
            ScanResult(fields, out, out.length())
        }

    fun discard(file: File?) { runCatching { file?.delete() } }

    /** Removes leftover photos (e.g. after the form is closed). */
    fun clearCache() { dir.listFiles()?.forEach { runCatching { it.delete() } } }

    private suspend fun ocrLines(bitmap: Bitmap, rotation: Int): List<String> {
        val text: Text = suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return text.textBlocks.flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }
    }

    /** Decodes with down-sampling (memory-safe for 50 MP photos) and applies the EXIF rotation. */
    private fun decodeUpright(uri: Uri, maxSide: Int): Bitmap? = withStream(uri) { input ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, bounds)
        bounds
    }?.let { bounds ->
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = withStream(uri) { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = withStream(uri) { ExifInterface(it).rotationDegrees } ?: 0
        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0) postRotate(rotation.toFloat()) }
        if (m.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it !== decoded) decoded.recycle() }
    }

    private fun <T> withStream(uri: Uri, block: (java.io.InputStream) -> T): T? =
        runCatching { context.contentResolver.openInputStream(uri)?.use(block) }.getOrNull()

    companion object {
        /** Longest side after compression: keeps small print readable at ~150–400 KB per photo. */
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 80
    }
}
