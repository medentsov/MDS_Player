package com.mds.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A4E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB6),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF6F5B3E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E0C0),
    onSecondaryContainer = Color(0xFF271A05),
    tertiary = Color(0xFF4A6350),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCE8D1),
    onTertiaryContainer = Color(0xFF062015),
    background = Color(0xFFFFF9F0),
    onBackground = Color(0xFF221A11),
    surface = Color(0xFFFFF9F0),
    onSurface = Color(0xFF221A11),
    surfaceVariant = Color(0xFFF1E2CC),
    onSurfaceVariant = Color(0xFF52473A),
    outline = Color(0xFF857463),
    outlineVariant = Color(0xFFD8C5AD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFF6E3B00),
    onPrimaryContainer = Color(0xFFFFDDB6),
    secondary = Color(0xFFDDC3A0),
    onSecondary = Color(0xFF3E2E14),
    secondaryContainer = Color(0xFF564428),
    onSecondaryContainer = Color(0xFFF9E0BE),
    tertiary = Color(0xFFB1CFB6),
    onTertiary = Color(0xFF1D3524),
    tertiaryContainer = Color(0xFF334C3A),
    onTertiaryContainer = Color(0xFFCDEBD2),
    background = Color(0xFF17120C),
    onBackground = Color(0xFFEDE0D1),
    surface = Color(0xFF17120C),
    onSurface = Color(0xFFEDE0D1),
    surfaceVariant = Color(0xFF4E4536),
    onSurfaceVariant = Color(0xFFD3C4B1),
    outline = Color(0xFF9C8D7C),
    outlineVariant = Color(0xFF4E4536),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MdsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
