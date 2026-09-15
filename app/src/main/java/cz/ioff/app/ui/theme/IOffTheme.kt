package cz.ioff.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IOffBackground = Color(0xFF050909)
val IOffSurface = Color(0xFF111719)
val IOffSurfaceHigh = Color(0xFF182024)
val IOffBorder = Color(0xFF263034)
val IOffText = Color(0xFFF3F7F5)
val IOffMuted = Color(0xFFA4ADAA)
val IOffGreen = Color(0xFF5DF58B)
val IOffGreenDeep = Color(0xFF0E3720)
val IOffYellow = Color(0xFFFFCE54)
val IOffRed = Color(0xFFFF535B)
val IOffBlue = Color(0xFF54C8FF)

private val IOffColors = darkColorScheme(
    primary = IOffGreen,
    onPrimary = IOffBackground,
    primaryContainer = IOffGreenDeep,
    onPrimaryContainer = IOffGreen,
    background = IOffBackground,
    onBackground = IOffText,
    surface = IOffSurface,
    onSurface = IOffText,
    surfaceVariant = IOffSurfaceHigh,
    onSurfaceVariant = IOffMuted,
    outline = IOffBorder,
    error = IOffRed
)

private val IOffTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 58.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
)

@Composable
fun IOffTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IOffColors, typography = IOffTypography, content = content)
}
