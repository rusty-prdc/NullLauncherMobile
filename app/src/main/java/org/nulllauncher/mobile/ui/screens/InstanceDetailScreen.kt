package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import org.nulllauncher.mobile.ui.theme.Warn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft

/**
 * Карточка сборки: моды (вкл/выкл/удалить), память сборки, переименование,
 * копия и удаление. Реальное управление файлами сборки.
 */
@Composable
fun InstanceDetailScreen(app: NullApp, nav: NavController, instanceId: String) {
    val scope = rememberCoroutineScope()

    var instance by remember { mutableStateOf(app.instances.get(instanceId)) }
    var mods by remember { mutableStateOf(listOf<File>()) }
    var newName by remember { mutableStateOf("") }
    var newMemory by remember { mutableFloatStateOf(2048f) }
    var maxMemory by remember { mutableStateOf(4096) }

    var showRename by remember { mutableStateOf(false) }
    var showDeleteMod by remember { mutableStateOf<File?>(null) }
    var showDeleteInstance by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        instance = app.instances.get(instanceId)
        mods = ModrinthService.installedMods(Storage.instanceMods(app, instanceId))
        instance?.let { newMemory = it.memoryMb.toFloat() }
    }

    LaunchedEffect(Unit) {
        maxMemory = app.settings.maxMemoryMb()
        refresh()
        newName = instance?.name ?: ""
    }

    val inst = instance
    if (inst == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Сборка не найдена", color = Err)
            TextButton(onClick = { nav.popBackStack() }) { Text("Назад") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    "${inst.versionId} · ${inst.loader}${inst.loaderVersion?.let { " $it" } ?: ""}",
                    fontSize = 11.sp, color = Mute,
                )
            }
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, "Переименовать", tint = Mute, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteInstance = true }) {
                Icon(Icons.Filled.Delete, "Удалить сборку", tint = Err)
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                message?.let {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AccSoft)) {
                        Text(it, Modifier.padding(10.dp), fontSize = 12.sp, color = Ok)
                    }
                }
            }

            // память сборки
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Память этой сборки", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${(newMemory / 1024f).let { "%.1f".format(it) }} ГБ", color = Acc2, fontSize = 13.sp)
                        }
                        Slider(
                            value = newMemory.coerceIn(512f, maxMemory.toFloat()),
                            onValueChange = { newMemory = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    app.instances.update(inst.copy(memoryMb = newMemory.toInt().coerceIn(512, maxMemory)))
                                    refresh()
                                }
                            },
                            valueRange = 512f..maxMemory.toFloat(),
                        )
                    }
                }
            }

            // статистика
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Модов: ${mods.size}", fontSize = 12.sp, color = Mute)
                            Text("Размер: ${fmtSize(Storage.dirSize(Storage.instanceDir(app, inst.id)))}", fontSize = 12.sp, color = Mute)
                        }
                        inst.lastPlayedAt.takeIf { it > 0 }?.let { ts ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Играли: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))}",
                                fontSize = 11.sp, color = Mute,
                            )
                        }
                    }
                }
            }

            // заголовок модов
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Моды", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("catalog") }) {
                        Icon(Icons.Filled.Add, null, tint = Acc, modifier = Modifier.size(16.dp))
                        Text(" Из каталога", color = Acc, fontSize = 12.sp)
                    }
                }
            }

            if (mods.isEmpty()) {
                item {
                    Text(
                        "Модов пока нет. Установи из каталога Modrinth — они попадут в эту сборку.",
                        fontSize = 12.sp, color = Mute,
                    )
                }
            }

            items(mods, key = { it.absolutePath }) { f ->
                val enabled = !f.name.endsWith(".disabled")
                val baseName = f.name.removeSuffix(".disabled")
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            ModrinthService.toggle(Storage.instanceMods(app, inst.id), baseName, !enabled)
                            refresh()
                        }) {
                            Text(
                                baseName.removeSuffix(".jar"),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (enabled) Color.White else Mute,
                                maxLines = 2,
                            )
                            Text(fmtSize(f.length()), fontSize = 10.5.sp, color = Mute)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                ModrinthService.toggle(Storage.instanceMods(app, inst.id), baseName, it)
                                refresh()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Acc),
                        )
                        IconButton(onClick = { showDeleteMod = f }) {
                            Icon(Icons.Filled.Delete, "Удалить мод", tint = Mute, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // переименование
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Переименовать сборку", fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Acc,
                        unfocusedBorderColor = Card2,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        app.instances.update(inst.copy(name = name))
                        refresh()
                    }
                    showRename = false
                }) { Text("Сохранить", color = Acc) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Отмена", color = Mute) }
            },
        )
    }

    // подтверждение удаления мода
    showDeleteMod?.let { f ->
        AlertDialog(
            onDismissRequest = { showDeleteMod = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить мод?", fontSize = 15.sp) },
            text = { Text(f.name, fontSize = 12.sp, color = Mute) },
            confirmButton = {
                TextButton(onClick = {
                    ModrinthService.deleteMod(Storage.instanceMods(app, inst.id), f)
                    refresh()
                    showDeleteMod = null
                }) { Text("Удалить", color = Err) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMod = null }) { Text("Отмена", color = Mute) }
            },
        )
    }

    // подтверждение удаления сборки
    if (showDeleteInstance) {
        AlertDialog(
            onDismissRequest = { showDeleteInstance = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить сборку?", fontSize = 15.sp) },
            text = {
                Text(
                    "«${inst.name}» и все её моды, миры и настройки будут удалены без возможности вернуть. Продолжить?",
                    fontSize = 12.5.sp, color = Mute,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.instances.delete(inst.id)
                    showDeleteInstance = false
                    nav.popBackStack()
                }) { Text("Удалить навсегда", color = Err) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteInstance = false }) { Text("Отмена", color = Mute) }
            },
        )
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> "%.0f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
