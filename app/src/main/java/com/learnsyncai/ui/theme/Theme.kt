package com.learnsyncai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoSoftBg,
    onPrimaryContainer = IndigoDark,
    secondary = EmeraldSuccess,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoftBg,
    onSecondaryContainer = EmeraldDark,
    tertiary = AmberFlame,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoftBg,
    onTertiaryContainer = AmberDark,
    error = RoseError,
    onError = Color.White,
    errorContainer = RoseSoftBg,
    onErrorContainer = RoseDark,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = IndigoSoftBg,
    secondary = EmeraldSuccess,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = EmeraldSoftBg,
    tertiary = AmberFlame,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberSoftBg,
    error = RoseError,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = RoseSoftBg,
    background = DarkBg,
    onBackground = Slate100,
    surface = DarkSurface,
    onSurface = Slate100,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Slate400,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun LearnSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
