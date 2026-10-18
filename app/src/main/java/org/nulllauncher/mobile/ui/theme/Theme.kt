package org.nulllauncher.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра nullLauncher Mobile: светлая, как в ПК-версии — белый + мятно-зелёный акцент
val Bg = Color(0xFFF4F7F5)          // фон приложения
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

private val NTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun NullTheme(content: @Composable () -> Unit) {
    // Как в ПК-лаунчере: всегда светлая тема
    MaterialTheme(colorScheme = Scheme, typography = NTypography, content = content)
}
