package com.genusit.prms

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.util.DocumentType
import com.genusit.prms.platform.DocumentScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real capture pipeline on Android (decode → EXIF/rotation → compression → ML Kit OCR → parser)
 * against synthetic cards drawn in the test. Made-up people and numbers only.
 */
@RunWith(AndroidJUnit4::class)
class DocumentScannerTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val scanner = DocumentScanner(ctx)

    private fun card(lines: List<String>, rotate: Float = 0f): Uri {
        val bmp = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 58f; typeface = Typeface.DEFAULT_BOLD }
        lines.forEachIndexed { i, l -> c.drawText(l, 80f, 130f + i * 105f, p) }
        val out = if (rotate == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotate) }, true)
        val f = File(ctx.cacheDir, "synthetic-${System.nanoTime()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return Uri.fromFile(f)
    }

    @Test fun readsAadhaarFront() = runBlocking {
        val r = scanner.scan(card(listOf("GOVERNMENT OF INDIA", "RAMESH KUMAR", "DOB: 01/01/1990", "MALE", "2345 6789 0124")), DocumentType.AADHAAR)
        assertEquals("234567890124", r.fields.number)
        assertEquals("Ramesh Kumar", r.fields.name)
        assertTrue("compressed photo kept small (${r.bytes} bytes)", r.bytes in 1..600_000)
    }

    @Test fun readsSidewaysPhoto() = runBlocking {
        val r = scanner.scan(card(listOf("GOVERNMENT OF INDIA", "RAMESH KUMAR", "DOB: 01/01/1990", "2345 6789 0124"), rotate = 90f), DocumentType.AADHAAR)
        assertEquals("234567890124", r.fields.number)
    }

    @Test fun readsPan() = runBlocking {
        val r = scanner.scan(card(listOf("INCOME TAX DEPARTMENT", "Permanent Account Number Card", "ABCPE1234F", "Name", "RAMESH KUMAR")), DocumentType.PAN)
        assertEquals("ABCPE1234F", r.fields.number)
        assertEquals("Ramesh Kumar", r.fields.name)
    }

    @Test fun blankPhotoIsUnreadable() = runBlocking {
        assertTrue(scanner.scan(card(emptyList()), DocumentType.AADHAAR).fields.isEmpty)
    }
}
