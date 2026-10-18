package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.launch.LaunchState
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Карточка сборки: запуск (как на главной), память, моды с вкл/выкл,
 * переименование, копия, удаление.
 */
@Composable
fun InstanceDetailScreen(app: NullApp, nav: NavController, instanceId: String) {
    val scope = rememberCoroutineScope()
    val account = app.currentAccount()
    val launcherState by app.launcher.state.collectAsState()
    val error by app.lastError.collectAsState()

    var instance by remember { mutableStateOf(app.instances.get(instanceId)) }
    var mods by remember { mutableStateOf(listOf<File>()) }
    var worlds by remember { mutableStateOf(listOf<File>()) }
    var packs by remember { mutableStateOf(listOf<File>()) }
    var newName by remember { mutableStateOf("") }
    var newMemory by remember { mutableFloatStateOf(2048f) }
    var maxMemory by remember { mutableStateOf(4096) }

    var showRename by remember { mutableStateOf(false) }
    var showDeleteMod by remember { mutableStateOf<File?>(null) }
    var showDeleteInstance by remember { mutableStateOf(false) }

    fun refresh() {
        instance = app.instances.get(instanceId)
        mods = ModrinthService.installedMods(Storage.instanceMods(app, instanceId))
        worlds = Storage.instanceSaves(app, instanceId).listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        packs = Storage.instanceResourcePacks(app, instanceId).listFiles { f -> f.isFile }
            ?.sortedBy { it.name } ?: emptyList()
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

    val busy = launcherState is LaunchState.Running || launcherState is LaunchState.Preparing

    fun launch() {
        val err = app.launcher.checkInstance(inst)
        if (err != null) {
            app.lastError.value = err
            return
        }
        app.lastError.value = null
        scope.launch {
            app.settings.setLastInstance(inst.id)
            app.instances.markPlayed(inst.id)
            app.launcher.start(inst, account, app.settings.currentJavaPath()) { l -> app.appendLog(l) }
            refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    "${inst.versionId} · ${loaderLabel(inst)}${inst.loaderVersion?.let { " $it" } ?: ""}",
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
            // ---------- запуск ----------
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AccSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    inst.name.firstOrNull()?.uppercase() ?: "S",
                                    fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Acc2,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Chip("${loaderLabel(inst)} ${inst.versionId}", primary = true)
                                Chip("${inst.memoryMb / 1024} ГБ")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { launch() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Acc),
                            enabled = !busy,
                        ) {
                            if (!busy) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                when (launcherState) {
                                    is LaunchState.Preparing -> "Запуск…"
                                    is LaunchState.Running -> "Игра идёт"
                                    else -> "Играть"
                                },
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            )
                        }
                        if (busy) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { app.launcher.stop() }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (launcherState is LaunchState.Running) "Остановить игру" else "Отмена",
                                    color = Err, fontSize = 13.sp,
                                )
                            }
                        }
                        (launcherState as? LaunchState.Preparing)?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it.stage, fontSize = 11.sp, color = Acc2)
                        }
                        (launcherState as? LaunchState.Failed)?.let { st ->
                            Spacer(Modifier.height(6.dp))
                            Text(st.title, fontSize = 11.sp, color = Err)
                            Text(st.details, fontSize = 10.sp, color = Mute, maxLines = 3)
                            // диагноз по правилам + отправка лога ИИ (Premium)
                            CrashDiagnosisCard(
                                app = app,
                                nav = nav,
                                log = app.launchLog.value + "\n" + st.details,
                                instanceId = inst.id,
                                ramMb = inst.memoryMb,
                            )
                        }
                        error?.let { e ->
                            Spacer(Modifier.height(6.dp))
                            Text(e, fontSize = 11.sp, color = Err, maxLines = 4)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (inst.lastPlayedAt > 0)
                                    "Играли: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(inst.lastPlayedAt))
                                else "Ещё не запускалась",
                                fontSize = 11.sp, color = Mute,
                            )
                            Text(
                                "Изменить настройки ↓",
                                fontSize = 11.sp, color = Acc2,
                                modifier = Modifier.clickable {
                                    // прокрутка LazyColumn к настройкам — просто показываем раздел
                                },
                            )
                        }
                    }
                }
            }

            // ---------- память сборки ----------
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Оперативная память", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

            // ---------- статистика ----------
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Модов: ${mods.size}", fontSize = 12.sp, color = Mute)
                            Text(
                                "Размер: ${fmtSize(Storage.dirSize(Storage.instanceDir(app, inst.id)))}",
                                fontSize = 12.sp, color = Mute,
                            )
                        }
                    }
                }
            }

            // ---------- миры ----------
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Миры", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${worlds.size}", fontSize = 12.sp, color = Mute)
                }
            }
            if (worlds.isEmpty()) {
                item {
                    Text(
                        "Миров пока нет — они появятся после первой игры в этой сборке.",
                        fontSize = 12.sp, color = Mute,
                    )
                }
            }
            items(worlds, key = { it.absolutePath }) { w ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                fmtSize(Storage.dirSize(w)) + " · " +
                                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(w.lastModified())),
                                fontSize = 10.5.sp, color = Mute,
                            )
                        }
                    }
                }
            }

            // ---------- ресурс-паки ----------
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ресурс-паки", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${packs.size}", fontSize = 12.sp, color = Mute)
                }
            }
            if (packs.isEmpty()) {
                item {
                    Text(
                        "Ресурс-паков нет. Скопируйте .zip в папку сборки — здесь они будут видны.",
                        fontSize = 12.sp, color = Mute,
                    )
                }
            }
            items(packs, key = { it.absolutePath }) { p ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(fmtSize(p.length()), fontSize = 10.5.sp, color = Mute)
                        }
                        IconButton(onClick = { p.delete(); refresh() }) {
                            Icon(Icons.Filled.Delete, "Удалить ресурс-пак", tint = Mute, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ---------- облачные бэкапы (Premium) ----------
            item {
                Spacer(Modifier.height(6.dp))
                CloudBackupSection(app = app, instanceId = inst.id)
            }

            // ---------- заголовок модов ----------
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Моды", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("catalog/${inst.id}") }) {
                        Icon(Icons.Filled.Add, null, tint = Acc2, modifier = Modifier.size(16.dp))
                        Text(" Из каталога", color = Acc2, fontSize = 12.sp)
                    }
                }
            }

            if (mods.isEmpty()) {
                item {
                    Text(
                        if (inst.loader == "vanilla")
                            "Vanilla не грузит моды — выбери сборку с Fabric/Forge/Quilt или ресурс-паки в каталоге."
                        else "Модов пока нет. Установи из каталога — они попадут именно в эту сборку.",
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
                                color = if (enabled) MaterialTheme.colorScheme.onBackground else Mute,
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
                }) { Text("Сохранить", color = Acc2) }
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
