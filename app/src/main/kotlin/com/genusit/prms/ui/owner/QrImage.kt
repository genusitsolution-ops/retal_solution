package com.genusit.prms.ui.owner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.genusit.prms.domain.model.UpiDetails
import com.genusit.prms.domain.util.Money

/**
 * 1.11: the UPI QR as a picture to keep — the code itself (exact server link, amount included) on white with a
 * quiet zone, and below it the payee, UPI ID, amount and reference in plain text, so the saved image is both
 * scannable from the gallery and understandable on its own.
 */
object QrImage {
    const val WIDTH = 900

    fun labelled(upi: UpiDetails, qrSize: Int = 720): Bitmap {
        val qr = qrBitmap(upi.uri, qrSize)
        val lines = listOf(upi.payee, upi.upiId, Money.format(upi.amount), upi.reference).filter { it.isNotBlank() }
        val top = 60; val lineH = 64
        val out = Bitmap.createBitmap(WIDTH, top + qr.height + 40 + lines.size * lineH + 40, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(qr, ((WIDTH - qr.width) / 2).toFloat(), top.toFloat(), null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER; textSize = 44f }
        var y = top + qr.height + 40 + 46
        lines.forEachIndexed { i, s ->
            p.typeface = if (i == 0 || i == 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            p.textSize = if (i == 2) 56f else 42f
            c.drawText(s.take(48), WIDTH / 2f, y.toFloat(), p)
            y += lineH
        }
        qr.recycle()
        return out
    }

    /** Android 10+: no permission needed (MediaStore); Android 7–9: the caller must hold WRITE_EXTERNAL_STORAGE. */
    fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < 29

    /** Saves as PNG in Pictures/RentalSolution. Returns the image Uri, or null on failure. */
    fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): Uri? {
        val file = name.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(60).ifBlank { "upi-qr" } + ".png"
        return if (Build.VERSION.SDK_INT >= 29) {
            val cr = ctx.contentResolver
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RentalSolution")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
            val ok = runCatching { cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } == true }.getOrDefault(false)
            if (!ok) { cr.delete(uri, null, null); return null }
            cr.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RentalSolution").apply { mkdirs() }
                val f = java.io.File(dir, file)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/png"), null)
                Uri.fromFile(f)
            }.getOrNull()
        }
    }
}
