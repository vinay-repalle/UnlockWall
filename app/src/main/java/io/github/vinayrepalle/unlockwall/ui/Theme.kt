package io.github.vinayrepalle.unlockwall.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light and clean, or near black. The user asked for no blue (and no default purple).

private fun ColorScheme.neutral(
    background: Color,
    ink: Color,
    onInk: Color,
    muted: Color,
    soft: Color,
    line: Color,
) = copy(
    primary = ink,
    onPrimary = onInk,
    primaryContainer = soft,
    onPrimaryContainer = ink,
    inversePrimary = onInk,
    secondary = ink,
    onSecondary = onInk,
    secondaryContainer = soft,
    onSecondaryContainer = ink,
    tertiary = ink,
    onTertiary = onInk,
    tertiaryContainer = soft,
    onTertiaryContainer = ink,
    background = background,
    onBackground = ink,
    surface = background,
    onSurface = ink,
    surfaceVariant = soft,
    onSurfaceVariant = muted,
    surfaceTint = ink,
    surfaceBright = background,
    surfaceDim = soft,
    surfaceContainerLowest = background,
    surfaceContainerLow = soft,
    surfaceContainer = soft,
    surfaceContainerHigh = soft,
    surfaceContainerHighest = soft,
    outline = muted,
    outlineVariant = line,
)

private val Light = lightColorScheme(error = Color(0xFFB3261E)).neutral(
    background = Color.White,
    ink = Color(0xFF161616),
    onInk = Color.White,
    muted = Color(0xFF5F5F5C),
    soft = Color(0xFFF0F0EE),
    line = Color(0xFFD8D8D4),
)

private val Dark = darkColorScheme(error = Color(0xFFF2B8B5)).neutral(
    background = Color(0xFF101010),
    ink = Color(0xFFEDEDEB),
    onInk = Color(0xFF161616),
    muted = Color(0xFFAEAEAA),
    soft = Color(0xFF222220),
    line = Color(0xFF3A3A37),
)

@Composable
fun UnlockWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
