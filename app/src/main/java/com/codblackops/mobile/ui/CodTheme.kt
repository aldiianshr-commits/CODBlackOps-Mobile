package com.codblackops.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CodOrange = Color(0xFFFF7A1A)
val CodOrangeDark = Color(0xFFB34F00)
val CodBackground = Color(0xFF0D0F11)
val CodSurface = Color(0xFF171A1D)
val CodSurfaceHigh = Color(0xFF22262A)
val CodGreen = Color(0xFF5BC27A)
val CodRed = Color(0xFFE5534B)
val CodYellow = Color(0xFFE8B931)

private val scheme = darkColorScheme(
    primary = CodOrange,
    onPrimary = Color.Black,
    primaryContainer = CodOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB8C2CC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF33393F),
    onSecondaryContainer = Color.White,
    background = CodBackground,
    onBackground = Color(0xFFE8EAED),
    surface = CodSurface,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = CodSurfaceHigh,
    onSurfaceVariant = Color(0xFFB0B6BC),
    error = CodRed,
    outline = Color(0xFF4A5056),
)

@Composable
fun CodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
