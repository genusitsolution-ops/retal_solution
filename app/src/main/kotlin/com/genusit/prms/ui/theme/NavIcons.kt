package com.genusit.prms.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Two-tone navigation icons in the brand colours (navy, teal, gold). They are drawn in full colour, so show them
 * with `Icon(icon, null, tint = Color.Unspecified)` or `Image(icon, null)`.
 */
object NavIcons {
    private val navy = Palette.Brand
    private val teal = Palette.Teal
    private val gold = Palette.Gold
    private val white = Color.White

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"

    private class B(name: String) {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fun fill(d: String, c: Color) = apply { b.addPath(addPathNodes(d), fill = SolidColor(c)) }
        fun stroke(d: String, c: Color, w: Float, fillColor: Color? = null) = apply {
            b.addPath(addPathNodes(d), fill = fillColor?.let { SolidColor(it) }, stroke = SolidColor(c), strokeLineWidth = w,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        fun build() = b.build()
    }

    val Home: ImageVector by lazy {
        B("nav_home")
            .fill("M5,12.2 L12,6.4 L19,12.2 L19,19.6 L5,19.6 Z", teal)
            .fill("M12,2.4 L1.6,11.2 L3.3,13.2 L12,5.9 L20.7,13.2 L22.4,11.2 Z", navy)
            .fill("M10.1,14.6 Q10.1,13.4 11.3,13.4 L12.7,13.4 Q13.9,13.4 13.9,14.6 L13.9,19.6 L10.1,19.6 Z", white)
            .fill("M13.2,9.6 h1.2 v1.2 h-1.2 Z M14.6,9.6 h1.2 v1.2 h-1.2 Z M13.2,11 h1.2 v1.2 h-1.2 Z M14.6,11 h1.2 v1.2 h-1.2 Z", gold)
            .fill("M3.2,19.8 Q12,18.2 20.8,19.8 L20.8,21 Q12,19.5 3.2,21 Z", gold)
            .build()
    }

    val Tenants: ImageVector by lazy {
        B("nav_tenants")
            .fill(circle(5.2f, 8.6f, 2.3f), teal)
            .fill(circle(18.8f, 8.6f, 2.3f), teal)
            .fill("M0.8,16.6 C0.8,13.7 2.8,12.1 5.2,12.1 C6.2,12.1 7.1,12.4 7.8,13 C6.9,14.1 6.3,15.3 6.1,16.6 Z", teal)
            .fill("M23.2,16.6 C23.2,13.7 21.2,12.1 18.8,12.1 C17.8,12.1 16.9,12.4 16.2,13 C17.1,14.1 17.7,15.3 17.9,16.6 Z", teal)
            .fill(circle(12f, 6.9f, 3.1f), navy)
            .fill("M6.7,17.3 C6.7,13.3 9.1,11 12,11 C14.9,11 17.3,13.3 17.3,17.3 Z", navy)
            .fill("M2.4,19.4 Q12,16.6 21.6,19.4 L21.6,20.5 Q12,17.8 2.4,20.5 Z", gold)
            .build()
    }

    val Bills: ImageVector by lazy {
        B("nav_bills")
            .fill("M16.2,1.9 L19.6,1.9 Q21.2,1.9 21.2,3.5 L21.2,7.2 L19.1,7.2 L19.1,4.4 Q19.1,4 18.7,4 L16.2,4 Z", gold)
            .stroke("M5.4,3 L16.4,3 L18.4,5 L18.4,21 L16.2,19.6 L14.1,21 L11.9,19.6 L9.7,21 L7.6,19.6 L5.4,21 Z", navy, 1.7f, white)
            .stroke("M9,6.8 L14.6,6.8 M9,9.1 L14.6,9.1 M9.5,6.8 C12.8,6.8 12.8,11.4 9.5,11.4 L13.4,15", teal, 1.5f)
            .stroke("M9,17.1 L14.8,17.1", navy, 1.3f)
            .build()
    }

    val Properties: ImageVector by lazy {
        val w = StringBuilder()
        fun win(x: Float, y: Float) { w.append("M$x,$y h1.3 v1.3 h-1.3 Z ") }
        listOf(4.4f to 11f, 6.6f to 11f, 4.4f to 14f, 6.6f to 14f, 16.4f to 13f, 18.6f to 13f, 16.4f to 16f, 18.6f to 16f,
            10.2f to 6.4f, 12.5f to 6.4f, 10.2f to 9.4f, 12.5f to 9.4f, 10.2f to 12.4f, 12.5f to 12.4f, 10.2f to 15.4f, 12.5f to 15.4f)
            .forEach { (x, y) -> win(x, y) }
        B("nav_properties")
            .fill("M2.8,9 L9,9 L9,20 L2.8,20 Z M15,11 L21.2,11 L21.2,20 L15,20 Z", teal)
            .fill("M8.8,4 L15.2,4 L15.2,20 L8.8,20 Z", navy)
            .stroke("M12,1.8 L12,4", navy, 1.4f)
            .fill(w.toString(), white)
            .fill("M1.4,21.3 Q12,19.2 22.6,21.3 L22.6,22.3 Q12,20.3 1.4,22.3 Z", gold)
            .build()
    }

    val More: ImageVector by lazy {
        B("nav_more")
            .stroke("M4,6.5 L20,6.5 M4,12 L20,12 M4,17.5 L12.6,17.5", navy, 2.3f)
            .stroke("M16.4,17.5 L19.6,17.5", gold, 2.6f)
            .build()
    }

    val Collect: ImageVector by lazy {
        B("nav_collect")
            .fill(circle(12f, 12f, 9.6f), gold)
            .stroke(circle(12f, 12f, 7.4f), Color(0xFFF7D97A), 1.1f)
            .stroke("M8.6,7.6 L15.4,7.6 M8.6,10.2 L15.4,10.2 M9.2,7.6 C13,7.6 13,12.9 9.2,12.9 L13.6,16.8", navy, 1.7f)
            .build()
    }

    val History: ImageVector by lazy {
        B("nav_history")
            .stroke("M4.3,9.2 A8.4,8.4 0 1,1 3.6,12.6", navy, 2f)
            .stroke("M2.2,6.4 L3.9,9.6 L7.2,8.3", navy, 2f)
            .stroke("M12,7.6 L12,12.4 L15.2,14.4", teal, 2.1f)
            .fill(circle(12f, 12.4f, 1.3f), gold)
            .build()
    }

    val Complaints: ImageVector by lazy {
        B("nav_complaints")
            .fill("M3,5.6 Q3,3.4 5.2,3.4 L18.8,3.4 Q21,3.4 21,5.6 L21,14.4 Q21,16.6 18.8,16.6 L10.4,16.6 L5.6,20.6 L5.6,16.6 L5.2,16.6 Q3,16.6 3,14.4 Z", teal)
            .stroke("M7,8.3 L17,8.3 M7,11.8 L13.6,11.8", white, 1.6f)
            .fill(circle(17.2f, 11.8f, 1.1f), gold)
            .build()
    }

    val Payments: ImageVector by lazy {
        B("nav_payments")
            .fill("M5.2,7.4 L15.6,3.4 L17.2,7.4 Z", teal)
            .fill("M3,9 Q3,7 5,7 L19,7 Q21,7 21,9 L21,18.4 Q21,20.4 19,20.4 L5,20.4 Q3,20.4 3,18.4 Z", navy)
            .fill("M14.9,11.4 L21.4,11.4 L21.4,16 L14.9,16 Q12.7,16 12.7,13.7 Q12.7,11.4 14.9,11.4 Z", gold)
            .fill(circle(15.4f, 13.7f, 1f), navy)
            .build()
    }
}
