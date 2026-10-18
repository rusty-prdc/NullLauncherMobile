package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nulllauncher.mobile.R
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Шаг 1 мастера: шаблон сборки, название, описание и цвет.
 * Иконки загрузчиков — из UI assets ПК-лаунчера.
 */
@Composable
fun InstanceCreationWizardStep1Template(
    template: String,
    onTemplateChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    color: String,
    onColorChange: (String) -> Unit,
    suggestedName: String,
) {
    val templates = listOf(
        Triple("clean", R.drawable.loader_vanilla, "Чистая"),
        Triple("fabric_burst", R.drawable.loader_fabric, "Fabric + Буст FPS"),
        Triple("shaders", R.drawable.loader_fabric, "Шейдеры"),
        Triple("forge", R.drawable.loader_forge, "Forge"),
        Triple("neoforge", R.drawable.loader_neoforge, "NeoForge"),
        Triple("quilt", R.drawable.loader_quilt, "Quilt"),
    )

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Шаблон и название", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        // сетка 2x3 как в ПК-версии
        templates.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (id, icon, label) ->
                    val selected = template == id
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
                            .border(if (selected) 1.5.dp else 0.dp, Acc2, RoundedCornerShape(14.dp))
                            .clickable { onTemplateChange(id) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Acc2 else Mute,
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(4.dp))

        androidx.compose.material3.OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название") },
            placeholder = { Text(suggestedName) },
        )

        Spacer(Modifier.height(10.dp))

        androidx.compose.material3.OutlinedTextField(
            value = description,
            onValueChange = { onDescriptionChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Описание (необязательно)") },
            minLines = 2,
        )

        Spacer(Modifier.height(10.dp))

        Text("Цвет сборки", fontSize = 12.sp, color = Mute)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("#3ecf8e", "#4a9eff", "#ff7849", "#a768ff", "#ff5c8a", "#f5b942").forEach { c ->
                val selected = color.equals(c, ignoreCase = true)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(c)))
                        .border(if (selected) 2.5.dp else 0.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp))
                        .clickable { onColorChange(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
