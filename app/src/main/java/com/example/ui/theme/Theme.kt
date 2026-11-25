package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val UttoDarkColorScheme = darkColorScheme(
    primary = DawnGold,
    onPrimary = OnDawnGold,
    primaryContainer = DawnGoldContainer,
    onPrimaryContainer = DawnGoldBright,
    secondary = HorizonCoral,
    onSecondary = OnHorizonCoral,
    secondaryContainer = HorizonCoralContainer,
    onSecondaryContainer = Color(0xFFFFCFC0),
    tertiary = TwilightViolet,
    onTertiary = Color(0xFF141A3B),
    tertiaryContainer = TwilightVioletContainer,
    onTertiaryContainer = Color(0xFFDEE4FF),
    background = NightObsidian,
    onBackground = StarlightMist,
    surface = NightDeep,
    onSurface = StarlightMist,
    surfaceVariant = NightSurface,
    onSurfaceVariant = CloudSecondary,
    surfaceContainerLowest = NightObsidian,
    surfaceContainerLow = NightDeep,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceElevated,
    surfaceContainerHighest = NightSurfaceHighest,
    outline = NightBorder,
    outlineVariant = Color(0xFF242B42),
    error = AlertRose,
    onError = Color(0xFF3B080D),
    errorContainer = AlertRoseContainer,
    onErrorContainer = Color(0xFFFFD4D4)
)

private val UttoLightColorScheme = lightColorScheme(
    primary = SunAmberDeep,
    onPrimary = Color.White,
    primaryContainer = SunAmberContainerLight,
    onPrimaryContainer = Color(0xFF3B1E00),
    secondary = Color(0xFFC9502C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDD2),
    onSecondaryContainer = Color(0xFF3E1103),
    tertiary = Color(0xFF4757A8),
    onTertiary = Color.White,
    background = WarmPaperBg,
    onBackground = WarmInk,
    surface = WarmPaperSurface,
    onSurface = WarmInk,
    surfaceVariant = WarmPaperSurfaceHigh,
    onSurfaceVariant = WarmInkSecondary,
    outline = Color(0xFFD5C8B5)
)

val UttoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun UttoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) UttoDarkColorScheme else UttoLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UttoTypography,
        shapes = UttoShapes,
        content = content
    )
}
