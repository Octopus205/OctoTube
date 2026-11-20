package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val OctoDarkColorScheme = darkColorScheme(
    primary = OctoCoral,
    onPrimary = OctoOnCoral,
    primaryContainer = OctoCoralContainer,
    onPrimaryContainer = OctoCoralSoft,
    secondary = OctoTeal,
    onSecondary = OctoBackgroundDark,
    secondaryContainer = OctoTealContainer,
    onSecondaryContainer = OctoTealSoft,
    tertiary = OctoAmber,
    onTertiary = OctoBackgroundDark,
    tertiaryContainer = OctoAmberContainer,
    onTertiaryContainer = OctoAmber,
    background = OctoBackgroundDark,
    onBackground = OctoTextPrimaryDark,
    surface = OctoSurfaceDark,
    onSurface = OctoTextPrimaryDark,
    surfaceVariant = OctoSurfaceVariantDark,
    onSurfaceVariant = OctoTextSecondaryDark,
    surfaceContainerHigh = OctoSurfaceHighDark,
    outline = OctoOutlineDark,
    outlineVariant = OctoOutlineDark
)

private val OctoLightColorScheme = lightColorScheme(
    primary = OctoCoral,
    onPrimary = OctoOnCoral,
    primaryContainer = ColorTokens.LightCoralContainer,
    onPrimaryContainer = ColorTokens.LightOnCoralContainer,
    secondary = ColorTokens.LightTeal,
    onSecondary = OctoSurfaceLight,
    secondaryContainer = ColorTokens.LightTealContainer,
    onSecondaryContainer = ColorTokens.LightOnTealContainer,
    tertiary = OctoAmber,
    onTertiary = OctoTextPrimaryLight,
    background = OctoBackgroundLight,
    onBackground = OctoTextPrimaryLight,
    surface = OctoSurfaceLight,
    onSurface = OctoTextPrimaryLight,
    surfaceVariant = OctoSurfaceVariantLight,
    onSurfaceVariant = OctoTextSecondaryLight,
    outline = OctoOutlineLight
)

private object ColorTokens {
    val LightCoralContainer = androidx.compose.ui.graphics.Color(0xFFFFDADF)
    val LightOnCoralContainer = androidx.compose.ui.graphics.Color(0xFF41000B)
    val LightTeal = androidx.compose.ui.graphics.Color(0xFF00897B)
    val LightTealContainer = androidx.compose.ui.graphics.Color(0xFFCFF8F3)
    val LightOnTealContainer = androidx.compose.ui.graphics.Color(0xFF00201C)
}

val OctoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OctoPipeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OctoDarkColorScheme
        else -> OctoLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OctoTypography,
        shapes = OctoShapes,
        content = content
    )
}
