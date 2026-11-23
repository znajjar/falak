package com.falak.livewallpaper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 dark tonal color system tailored to Falak's obsidian night sky
 * and warm incandescent starlight ivory palette.
 */
object FalakPalette {
    val ObsidianBg = Color(0xFF0B0C0F)
    val StageBackdropTop = Color(0xFF13161E)
    val StageBackdropBottom = Color(0xFF0C0E13)
    val StarlightIvory = Color(0xFFF5E6CE)
    val WarmGlow = Color(0xFFFFF5E1)
    val LiveDotGreen = Color(0xFF7DD89E)
}

private val FalakDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF5E6CE),
    onPrimary = Color(0xFF1D1710),
    primaryContainer = Color(0xFF3E3528),
    onPrimaryContainer = Color(0xFFFFF4E3),

    secondary = Color(0xFFC2CBD9),
    onSecondary = Color(0xFF1B222E),
    secondaryContainer = Color(0xFF252B37),
    onSecondaryContainer = Color(0xFFDDE5F3),

    tertiary = Color(0xFFE9CA9B),
    onTertiary = Color(0xFF261B0B),
    tertiaryContainer = Color(0xFF3D301D),
    onTertiaryContainer = Color(0xFFFDE8C8),

    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFECEFF6),

    surface = Color(0xFF101217),
    onSurface = Color(0xFFECEFF6),
    surfaceVariant = Color(0xFF222631),
    onSurfaceVariant = Color(0xFFA2ABB9),

    surfaceContainerLowest = Color(0xFF090A0D),
    surfaceContainerLow = Color(0xFF14161D),
    surfaceContainer = Color(0xFF1A1D26),
    surfaceContainerHigh = Color(0xFF222632),
    surfaceContainerHighest = Color(0xFF2C3140),

    outline = Color(0xFF6F7788),
    outlineVariant = Color(0xFF303645),

    inverseSurface = Color(0xFFE7EBF3),
    inverseOnSurface = Color(0xFF14161C),
    inversePrimary = Color(0xFF6B583D),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xFF000000)
)

private val FalakTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp
    )
)

private val FalakShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FalakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FalakDarkColorScheme,
        typography = FalakTypography,
        shapes = FalakShapes,
        content = content
    )
}
