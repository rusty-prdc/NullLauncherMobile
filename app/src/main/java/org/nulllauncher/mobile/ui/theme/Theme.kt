package org.nulllauncher.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра nullLauncher: чёрный/графит + фиолетово-синие акценты
val Bg = Color(0xFF0B0B10)
val Card = Color(0xFF14141C)
val Card2 = Color(0xFF1B1B26)
val Line = Color(0x12FFFFFF)
val Txt = Color(0xFFF2F2F7)
val Mute = Color(0xFF9A9AB0)
val Acc = Color(0xFF8B5CF6)
val Acc2 = Color(0xFF4F8CFF)
val Ok = Color(0xFF3ECF8E)
val Warn = Color(0xFFFFB020)
val Err = Color(0xFFFF5C5C)

private val Scheme = darkColorScheme(
    primary = Acc,
    onPrimary = Color.White,
    secondary = Acc2,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Txt,
    surface = Card,
    onSurface = Txt,
    surfaceVariant = Card2,
    onSurfaceVariant = Mute,
    outline = Line,
    error = Err,
)

private val NTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun NullTheme(content: @Composable () -> Unit) {
    // Приложение всегда в тёмной теме — как в макете
    MaterialTheme(colorScheme = Scheme, typography = NTypography, content = content)
}
