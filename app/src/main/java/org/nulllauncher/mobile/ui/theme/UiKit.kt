package org.nulllauncher.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- палитра градиентов (как в макете) ----------
private val Green = Color(0xFF34D07F) to Color(0xFF128A50)
private val Blue = Color(0xFF7BA7F0) to Color(0xFF3F6FD1)
private val Purple = Color(0xFFC79BF2) to Color(0xFF8B5CD6)
private val Orange = Color(0xFFFFC96B) to Color(0xFFE08A1F)
private val Red = Color(0xFFFF8A7A) to Color(0xFFDE4D4D)

private val palettes = listOf(Green, Blue, Purple, Orange, Red)

/** Основной мятный градиент кнопок */
val AccGrad: Brush
    get() = Brush.linearGradient(listOf(Color(0xFF34D07F), Color(0xFF1FA35E)))

/** Глубокий мятный градиент (иконки, аватарки) */
val AccGradDeep: Brush
    get() = Brush.linearGradient(listOf(Color(0xFF34D07F), Color(0xFF128A50)))

/**
 * Стабильный градиент для объекта: одна и та же строка всегда даёт один цвет.
 * Используется для иконок сборок и аватарок.
 */
fun instanceGradient(seed: String): Brush {
    val idx = (seed.hashCode() % palettes.size + palettes.size) % palettes.size
    val p = palettes[idx]
    return Brush.linearGradient(listOf(p.first, p.second))
}

/** Градиентная кнопка-пилюля как в макете */
@Composable
fun GradButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    fontSize: TextUnit = 13.sp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) AccGrad
                else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Mute,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
    }
}

/** Цветной бейдж-пилюля (категория мода, статус) */
@Composable
fun Badge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
