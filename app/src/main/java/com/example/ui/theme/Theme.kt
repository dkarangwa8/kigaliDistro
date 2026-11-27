package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = KigaliEmerald,
    onPrimary = Color.White,
    primaryContainer = KigaliMintTint,
    onPrimaryContainer = KigaliEmeraldDeep,
    secondary = BrewGold,
    onSecondary = Color.White,
    secondaryContainer = BrewGoldContainer,
    onSecondaryContainer = BrewGoldDarkText,
    tertiary = KigaliTerracotta,
    onTertiary = Color.White,
    tertiaryContainer = KigaliTerracottaContainer,
    onTertiaryContainer = Color(0xFF471505),
    background = WarmIvoryCanvas,
    onBackground = InkCharcoal,
    surface = AlabasterCard,
    onSurface = InkCharcoal,
    surfaceVariant = SoftSandSurface,
    onSurfaceVariant = SlateMist,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7E0D6),
    error = TrafficHeavyRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = KigaliEmeraldDeep,
    primaryContainer = KigaliEmerald,
    onPrimaryContainer = KigaliMintTint,
    secondary = DarkGoldSecondary,
    onSecondary = BrewGoldDarkText,
    secondaryContainer = Color(0xFF5A3700),
    onSecondaryContainer = BrewGoldContainer,
    tertiary = Color(0xFFFF9873),
    onTertiary = Color(0xFF471505),
    tertiaryContainer = Color(0xFF6B2810),
    onTertiaryContainer = KigaliTerracottaContainer,
    background = DarkEmeraldCanvas,
    onBackground = DarkIvoryText,
    surface = DarkEmeraldSurface,
    onSurface = DarkIvoryText,
    surfaceVariant = DarkEmeraldSurfaceHigh,
    onSurfaceVariant = DarkSlateText,
    outline = DarkOutline,
    outlineVariant = Color(0xFF21342D),
    error = Color(0xFFFF897D),
    onError = Color(0xFF5F140D)
)

val KigaliShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KigaliDistroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = KigaliShapes,
        content = content
    )
}
