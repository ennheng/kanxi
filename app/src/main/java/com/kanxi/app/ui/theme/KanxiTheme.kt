package com.kanxi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val KanxiRed = Color(0xFF7A1F24)
private val KanxiDarkRed = Color(0xFF531317)
private val KanxiCream = Color(0xFFFFF8E8)
private val KanxiGold = Color(0xFFF4C95D)
private val KanxiInk = Color(0xFF241A17)
private val KanxiMuted = Color(0xFF655C58)

private val LightColors = lightColorScheme(
    primary = KanxiRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DADD),
    onPrimaryContainer = KanxiDarkRed,
    secondary = Color(0xFF765B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE691),
    onSecondaryContainer = Color(0xFF241A00),
    background = KanxiCream,
    onBackground = KanxiInk,
    surface = Color(0xFFFFFBF4),
    onSurface = KanxiInk,
    surfaceVariant = Color(0xFFF0E4D4),
    onSurfaceVariant = KanxiMuted,
    outline = Color(0xFF756967),
    error = Color(0xFF9F1D22),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B9),
    onPrimary = Color(0xFF4A0009),
    primaryContainer = Color(0xFF651018),
    onPrimaryContainer = Color(0xFFFFDADC),
    secondary = KanxiGold,
    onSecondary = Color(0xFF3E2E00),
    background = Color(0xFF1C1412),
    onBackground = Color(0xFFFFEDE5),
    surface = Color(0xFF251B18),
    onSurface = Color(0xFFFFEDE5),
)

enum class FontSizePreference(val label: String, val multiplier: Float) {
    Standard("标准", 1.0f),
    Large("大字", 1.12f),
    ExtraLarge("特大", 1.25f),
}

val LocalFontSizePreference = staticCompositionLocalOf { FontSizePreference.Large }

private fun scaledTypography(scale: Float) = Typography(
    displaySmall = textStyle(34.sp, scale, FontWeight.Bold),
    headlineLarge = textStyle(30.sp, scale, FontWeight.Bold),
    headlineMedium = textStyle(28.sp, scale, FontWeight.Bold),
    headlineSmall = textStyle(24.sp, scale, FontWeight.Bold),
    titleLarge = textStyle(24.sp, scale, FontWeight.Bold),
    titleMedium = textStyle(22.sp, scale, FontWeight.SemiBold),
    titleSmall = textStyle(20.sp, scale, FontWeight.SemiBold),
    bodyLarge = textStyle(20.sp, scale),
    bodyMedium = textStyle(18.sp, scale),
    bodySmall = textStyle(16.sp, scale),
    labelLarge = textStyle(20.sp, scale, FontWeight.SemiBold),
    labelMedium = textStyle(18.sp, scale, FontWeight.SemiBold),
    labelSmall = textStyle(16.sp, scale, FontWeight.Medium),
)

private fun textStyle(
    size: TextUnit,
    scale: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size * scale,
    lineHeight = size * scale * 1.45f,
    fontWeight = weight,
)

@Composable
fun KanxiTheme(
    darkTheme: Boolean = false,
    fontSizePreference: FontSizePreference = FontSizePreference.Large,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFontSizePreference provides fontSizePreference) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = scaledTypography(fontSizePreference.multiplier),
            content = content,
        )
    }
}

