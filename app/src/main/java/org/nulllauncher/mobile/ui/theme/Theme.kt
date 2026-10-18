package org.nulllauncher.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра nullLauncher Mobile: светлая, как в ПК-версии — белый + мятно-зелёный акцент
val Bg = Color(0xFFF4F7F5)          // фон приложения (светлая тема)
val Card = Color(0xFFFFFFFF)        // карточки
val Card2 = Color(0xFFEDF2EF)       // вторичный фон (иконки, чипы, поля)
val Line = Color(0xFFE2E9E5)        // рамки и разделители
val Txt = Color(0xFF12211A)         // основной текст
val Mute = Color(0xFF6B7C74)        // вторичный текст
val Acc = Color(0xFF2FBF71)         // мятно-зелёный акцент (как в ПК)
val Acc2 = Color(0xFF1FA35E)        // тёмнее — для нажатий/текста на светлом
val AccSoft = Color(0xFFE3F7EC)     // светло-зелёная пилюля (активные фильтры)
val Ok = Acc
val Warn = Color(0xFFD9922B)
val Err = Color(0xFFDE4D4D)
val ErrSoft = Color(0xFFFBE9E9)     // фон ошибок на светлом

// Тёмная тема: тот же мятный акцент, тёмные поверхности
private val DarkBg = Color(0xFF101512)
private val DarkCard = Color(0xFF182019)
private val DarkCard2 = Color(0xFF222B25)
private val DarkLine = Color(0xFF2C3730)
private val DarkTxt = Color(0xFFE9F1EC)
private val DarkMute = Color(0xFF8E9C94)
private val DarkAccSoft = Color(0xFF1B3A29)
private val DarkErrSoft = Color(0xFF3A2222)

private val Scheme = lightColorScheme(
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
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Acc,
    onPrimary = Color.Black,
    secondary = Color(0xFF3ECF8E),
    onSecondary = Color.Black,
    background = DarkBg,
    onBackground = DarkTxt,
    surface = DarkCard,
    onSurface = DarkTxt,
    surfaceVariant = DarkCard2,
    onSurfaceVariant = DarkMute,
    outline = DarkLine,
    error = Err,
    onError = Color.White,
)

private val NTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

/**
 * Тема приложения: "light" | "dark" | "system" (настройка в Профиле).
 * Светлая — как в ПК-лаунчере; тёмная сохраняет мятный акцент.
 */
@Composable
fun NullTheme(mode: String = "light", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "system" -> isSystemInDarkTheme()
        else -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else Scheme,
        typography = NTypography,
        content = content,
    )
}
