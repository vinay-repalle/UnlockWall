package io.github.vinayrepalle.unlockwall.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF161616)
private val Muted = Color(0xFF6B6B68)
private val Soft = Color(0xFFF0F0EE)
private val Line = Color(0xFFD8D8D4)

// Light and clean. The user asked for no blue (the default purple is replaced too).
private val Colors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Soft,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Soft,
    onSurfaceVariant = Muted,
    surfaceContainerHighest = Soft,
    outline = Muted,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

@Composable
fun UnlockWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
