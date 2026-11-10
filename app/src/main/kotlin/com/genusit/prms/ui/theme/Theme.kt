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

/*
 * 1.14 identity (matches the logo): deep navy, teal and gold on white cards and a soft lavender-white canvas.
 * Every screen reads its colours from here, so the whole app follows one palette.
 */
object Palette {
    // navy — primary actions, titles, selected states
    val Brand = Color(0xFF0B2A5B)
    val BrandDark = Color(0xFF061733)
    // lavender — selected navigation pill, soft containers
    val BrandSoft = Color(0xFFE8E6FA)
    // teal — the second brand colour (charts, highlights, wallet cards)
    val Accent = Color(0xFF0E7C86)
    val Teal = Color(0xFF0E7C86)
    val TealBright = Color(0xFF17A9AE)
    val TealSoft = Color(0xFFD9F2F1)
    // gold — the logo's accent: key call-to-action on dark cards, small highlights
    val Gold = Color(0xFFE2A72E)
    val GoldBright = Color(0xFFF7C948)
    val GoldSoft = Color(0xFFFFF4D9)
    val NavSelected = Color(0xFF3127A8)

    val Shadow = Color(0xFF3B3A8C).copy(alpha = .14f)
    val Canvas = Color(0xFFF6F6FC)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0E1630)
    val Muted = Color(0xFF636B80)
    val Line = Color(0xFFE5E7F0)
    val Success = Color(0xFF15A05A)
    val SuccessSoft = Color(0xFFDDF6E7)
    val Warning = Color(0xFFD58A06)
    val WarningSoft = Color(0xFFFFF1D2)
    val Danger = Color(0xFFDC3545)
    val DangerSoft = Color(0xFFFDE3E5)
    val Info = Color(0xFF0E7C86)
    val InfoSoft = Color(0xFFD9F2F1)
    val Purple = Color(0xFF6C4CE0)
    val PurpleSoft = Color(0xFFEDE8FD)

    /** Main money card of each dashboard: navy into teal. */
    val WalletGradient = Brush.linearGradient(listOf(Color(0xFF0A2350), Color(0xFF0B3D63), Color(0xFF0B6670)))
    /** Splash and dark headers. */
    val HeaderGradient = Brush.verticalGradient(listOf(Color(0xFF061733), Color(0xFF0A2A55), Color(0xFF0A4F62)))
    /** Gold call-to-action on dark cards. */
    val GoldGradient = Brush.horizontalGradient(listOf(Color(0xFFF7C948), Color(0xFFE2A72E)))
    /** Navy primary button. */
    val NavyGradient = Brush.horizontalGradient(listOf(Color(0xFF0B2A5B), Color(0xFF14408A)))
}

private val colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Color.White,
    primaryContainer = Palette.BrandSoft,
    onPrimaryContainer = Palette.Brand,
    secondary = Palette.Teal,
    onSecondary = Color.White,
    // selected filter chips / tonal buttons: navy pill with white text
    secondaryContainer = Palette.Brand,
    onSecondaryContainer = Color.White,
    tertiary = Palette.Gold,
    onTertiary = Palette.BrandDark,
    tertiaryContainer = Palette.GoldSoft,
    onTertiaryContainer = Color(0xFF6B4A00),
    background = Palette.Canvas,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF1F2F8),
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color(0xFFF5F5FB),
    surfaceContainerHigh = Color(0xFFF1F1F8),
    surfaceContainerHighest = Color(0xFFECECF5),
    outline = Color(0xFFD5D8E5),
    outlineVariant = Palette.Line,
    error = Palette.Danger,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PrmsTheme(content: @Composable () -> Unit) {
    // Devanagari renders with the system Noto font; line heights above leave room for matras.
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
