package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.touch.TouchElement
import org.nulllauncher.mobile.data.touch.TouchProfiles
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Mute
import kotlin.math.roundToInt

/**
 * Редактор сенсорного управления (бесплатно для всех):
 * пресеты профилей (Классика/PvE/Стройка), перетаскивание элементов пальцем,
 * размер и прозрачность, палитра кнопок. Сохраняется в папку сборки.
 */
@Composable
fun TouchEditorScreen(app: NullApp, nav: NavController, instanceId: String) {
    var profiles by remember { mutableStateOf(TouchProfiles.load(app, instanceId)) }
    var activeIdx by remember { mutableIntStateOf(0) }
    var elements by remember { mutableStateOf(profiles.getOrNull(0)?.elements ?: emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dirty by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val selected = elements.firstOrNull { it.id == selectedId }

    fun update(new: List<TouchElement>) {
        elements = new
        dirty = true
    }

    fun switchProfile(idx: Int) {
        // запоминаем правки текущего профиля
        profiles = profiles.mapIndexed { i, p -> if (i == activeIdx) p.copy(elements = elements) else p }
        activeIdx = idx
        elements = profiles.getOrNull(idx)?.elements ?: emptyList()
        selectedId = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text("Управление", style = MaterialTheme.typography.titleLarge)
                Text("Профили раскладки · элементы двигаются пальцем", fontSize = 11.sp, color = Mute)
            }
            TextButton(onClick = {
                profiles = profiles.mapIndexed { i, p -> if (i == activeIdx) p.copy(elements = elements) else p }
                TouchProfiles.save(app, instanceId, profiles)
                dirty = false
            }) {
                Text(if (dirty) "Сохранить ●" else "Сохранено", color = if (dirty) Acc2 else Mute, fontSize = 12.sp)
            }
        }

        // ---------- профили ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            profiles.forEachIndexed { i, p ->
                val on = i == activeIdx
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) AccSoft else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .clickable { switchProfile(i) },
                ) {
                    Text(
                        p.name,
                        fontSize = 12.sp,
                        color = if (on) Acc2 else Mute,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- холст ----------
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A2420))
                .onSizeChanged { canvasSize = it },
        ) {
            Text(
                "Свайп-зона = камера · джойстик = движение",
                color = Color(0x66FFFFFF), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )

            if (canvasSize.width > 0 && canvasSize.height > 0) {
                val cw = canvasSize.width.toFloat()
                val ch = canvasSize.height.toFloat()

                elements.forEach { el ->
                    val sizePx = cw * el.sizePct / 100f
                    val cxPx = cw * el.xPct / 100f
                    val cyPx = ch * el.yPct / 100f
                    val on = el.id == selectedId
                    val round = el.type == "joystick" || el.type == "swipe"

                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (cxPx - sizePx / 2).roundToInt(),
                                    (cyPx - sizePx / 2).roundToInt(),
                                )
                            }
                            .size(with(density) { sizePx.toDp() })
                            .clip(RoundedCornerShape(if (round) 999.dp else 14.dp))
                            .background(
                                (if (el.type == "swipe") Color(0x44FFFFFF) else Color(0xD92FBF71))
                                    .copy(alpha = el.opacity),
                            )
                            .then(
                                if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(if (round) 999.dp else 14.dp))
                                else Modifier,
                            )
                            .pointerInput(el.id) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val dx = drag.x * 100f / cw
                                    val dy = drag.y * 100f / ch
                                    update(
                                        elements.map {
                                            if (it.id == el.id) it.copy(
                                                xPct = (it.xPct + dx).coerceIn(2f, 98f),
                                                yPct = (it.yPct + dy).coerceIn(2f, 98f),
                                            ) else it
                                        },
                                    )
                                    selectedId = el.id
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                el.type == "joystick" -> "◎"
                                el.type == "swipe" -> "⟲"
                                else -> el.label
                            },
                            color = Color.White,
                            fontSize = with(density) { (sizePx / 3f).toSp() },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- палитра ----------
        Text("Добавить кнопку", fontSize = 12.sp, color = Mute, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        val palette = listOf(
            "key.space" to "Прыжок ▲", "key.shift" to "Присесть ▼", "key.e" to "Инвентарь E",
            "key.q" to "Выбросить Q", "key.r" to "R", "key.f" to "F", "key.esc" to "Меню ☰",
            "key.mouse.left" to "Атака ⚔", "key.mouse.right" to "Использовать ✋",
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(palette) { (key, label) ->
                TextButton(onClick = {
                    val nid = "$key-${System.currentTimeMillis() % 1000000}"
                    update(elements + TouchElement(nid, "button", key, label.substringAfter(' ').ifBlank { label }, 50f, 50f, 10f))
                    selectedId = nid
                }) {
                    Icon(Icons.Filled.Add, null, tint = Acc2, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(label, fontSize = 11.5.sp, color = Acc2)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---------- свойства ----------
        selected?.let { el ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${el.label} · ${el.key}",
                            fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            update(elements.filterNot { it.id == el.id })
                            selectedId = null
                        }) { Icon(Icons.Filled.Delete, "Удалить", tint = Mute, modifier = Modifier.size(17.dp)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Размер", fontSize = 11.5.sp, color = Mute, modifier = Modifier.width(52.dp))
                        Slider(
                            value = el.sizePct,
                            onValueChange = { v -> update(elements.map { if (it.id == el.id) it.copy(sizePct = v) else it }) },
                            valueRange = 5f..35f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Прозр.", fontSize = 11.5.sp, color = Mute, modifier = Modifier.width(52.dp))
                        Slider(
                            value = el.opacity,
                            onValueChange = { v -> update(elements.map { if (it.id == el.id) it.copy(opacity = v) else it }) },
                            valueRange = 0.2f..1f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } ?: Text(
            "Нажми элемент — появятся размер и прозрачность. Тяни — двигай.",
            fontSize = 11.5.sp, color = Mute, modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))
    }
}
