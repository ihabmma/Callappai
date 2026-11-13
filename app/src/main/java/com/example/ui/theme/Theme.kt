package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPulse,
    onPrimary = Color(0xFF00241A),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFF7AF5CF),
    secondary = CyanSignal,
    onSecondary = Color(0xFF002033),
    secondaryContainer = Color(0xFF0B354D),
    onSecondaryContainer = Color(0xFFBFE9FF),
    tertiary = AmberAlert,
    onTertiary = Color(0xFF2B1800),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = CrimsonStop,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF003326),
    secondary = CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F1FD),
    onSecondaryContainer = Color(0xFF00273D),
    tertiary = AmberAlert,
    onTertiary = Color.White,
    background = MintBgLight,
    onBackground = TextPrimaryLight,
    surface = MintSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = MintSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = MintOutlineLight,
    error = CrimsonStop,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun SmartCallResponderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
