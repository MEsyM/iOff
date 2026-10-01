package com.dualactionwindows.dawdrive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LoneRiderColors {
    val Background = Color(0xFF05070B)
    val Surface = Color(0xFF0C1118)
    val SurfaceRaised = Color(0xFF111923)
    val Border = Color(0xFF1E3552)
    val TextPrimary = Color(0xFFF4F7FB)
    val TextSecondary = Color(0xFF9AA8B8)
    val Blue = Color(0xFF2D8CFF)
    val Cyan = Color(0xFF22D3EE)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
}

private val LoneRiderScheme = darkColorScheme(
    primary = LoneRiderColors.Blue,
    secondary = LoneRiderColors.Cyan,
    tertiary = LoneRiderColors.Purple,
    background = LoneRiderColors.Background,
    surface = LoneRiderColors.Surface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = LoneRiderColors.TextPrimary,
    onSurface = LoneRiderColors.TextPrimary,
    error = LoneRiderColors.Red
)

@Composable
fun LoneRiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoneRiderScheme,
        content = content
    )
}
