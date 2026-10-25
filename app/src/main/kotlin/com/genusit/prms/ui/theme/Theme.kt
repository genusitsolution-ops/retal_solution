package com.genusit.prms.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* FiMobile-inspired palette: indigo/blue brand, soft grey canvas, white rounded cards. */
object Palette {
    val Brand = Color(0xFF4F46E5)
    val BrandDark = Color(0xFF1E3A8A)
    val BrandSoft = Color(0xFFE0E7FF)
    val Canvas = Color(0xFFF4F6FB)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Success = Color(0xFF16A34A)
    val SuccessSoft = Color(0xFFDCFCE7)
    val Warning = Color(0xFFD97706)
    val WarningSoft = Color(0xFFFEF3C7)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFEE2E2)
    val Info = Color(0xFF0891B2)
    val InfoSoft = Color(0xFFCFFAFE)

    val WalletGradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF1E40AF), Color(0xFF1E3A8A)))
    val HeaderGradient = Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF4F46E5)))
}

private val colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Color.White,
    primaryContainer = Palette.BrandSoft,
    onPrimaryContainer = Palette.BrandDark,
    secondary = Palette.BrandDark,
    background = Palette.Canvas,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Palette.Danger,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun PrmsTheme(content: @Composable () -> Unit) {
    // Devanagari renders with the system Noto font; line heights above leave room for matras.
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
