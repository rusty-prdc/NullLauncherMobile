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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.nulllauncher.mobile.InstallProgress
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.loaders.LoaderService
import org.nulllauncher.mobile.data.modpacks.ModpackProgress
import org.nulllauncher.mobile.data.modpacks.ModpackService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Сборки: создание, переименование, копия, удаление с подтверждением, память.
 */
@Composable
fun InstancesScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var instances by remember { mutableStateOf(app.instances.list()) }
    var editTarget by remember { mutableStateOf<Instance?>(null) }
    var deleteTarget by remember { mutableStateOf<Instance?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importFileName by remember { mutableStateOf<String?>(null) }
    var importStage by remember { mutableStateOf<String?>(null) }
    var importProgress by remember { mutableStateOf<ModpackProgress?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // выбор .mrpack в системном файловом менеджере
    val pickPack = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importUri = uri
            importUrl = ""
            importFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "файл.mrpack"
            importError = null
        }
    }

    fun refresh() { instances = app.instances.list() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Сборки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showImport = true }) {
                Icon(Icons.Filled.FileDownload, "Импорт модпака", tint = Acc2)
            }
            IconButton(onClick = { nav.navigate("create_instance") }) {
                Icon(Icons.Filled.Add, "Создать сборку", tint = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(instances, key = { it.id }) { inst ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate("instance/${inst.id}") },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(AccSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    inst.name.firstOrNull()?.uppercase() ?: "S",
                                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Acc2,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(inst.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${loaderLabel(inst)} ${inst.versionId} · ${inst.memoryMb / 1024} ГБ RAM · ${app.modCount(inst.id)} модов",
                                    fontSize = 11.sp, color = Mute,
                                )
                            }
                            Text("›", fontSize = 20.sp, color = Mute)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAction("Управлять", Icons.Filled.Settings) { nav.navigate("instance/${inst.id}") }
                            SmallAction("Копия", Icons.Filled.ContentCopy) {
                                scope.launch {
                                    app.instances.copy(inst.id, "${inst.name} (копия)")
                                    refresh()
                                }
                            }
                            SmallAction("Удалить", Icons.Filled.Delete, danger = true) { deleteTarget = inst }
                        }
                    }
                }
            }
        }
    }

    // ---------- диалог переименования ----------
    editTarget?.let { target ->
        var name by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Переименовать") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    app.instances.update(target.copy(name = name.ifBlank { target.name }))
                    editTarget = null
                    refresh()
                }) { Text("Сохранить", color = Acc) }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Отмена", color = Mute) } },
        )
    }

    // ---------- подтверждение удаления ----------
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить сборку?") },
            text = { Text("«${target.name}» и все её моды, миры и настройки будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    app.instances.delete(target.id)
                    deleteTarget = null
                    refresh()
                }) { Text("Удалить", color = Err) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена", color = Mute) } },
        )
    }

    // ---------- импорт модпака .mrpack (бесплатно для всех) ----------
    if (showImport) {
        AlertDialog(
            onDismissRequest = { if (importStage == null) showImport = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Импорт модпака", fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "Вставь ссылку на проект Modrinth (modrinth.com/modpack/…) или на файл .mrpack — " +
                            "либо выбери файл с устройства. Сборку, моды и загрузчик поставлю сам.",
                        fontSize = 12.sp, color = Mute,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it; importUri = null; importFileName = null },
                        singleLine = true,
                        enabled = importStage == null,
                        label = { Text("Ссылка на проект или .mrpack") },
                        placeholder = { Text("https://modrinth.com/modpack/…") },
                        isError = importError != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            pickPack.launch(arrayOf("*/*"))
                        },
                        enabled = importStage == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FileDownload, null, tint = Acc2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            importFileName ?: "Выбрать файл с устройства…",
                            fontSize = 12.5.sp, color = Acc2, maxLines = 1,
                        )
                    }
                    importError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 11.sp, color = Err)
                    }
                    importStage?.let { st ->
                        Spacer(Modifier.height(10.dp))
                        Text(st, fontSize = 12.sp, color = Acc2)
                        importProgress?.let { p ->
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (p.total > 0) {
                                Text("${p.done} / ${p.total}", fontSize = 10.5.sp, color = Mute)
                            }
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importStage == null && (importUrl.isNotBlank() || importUri != null),
                    onClick = {
                        importError = null
                        scope.launch {
                            importStage = "Подготовка…"
                            importProgress = null
                            try {
                                val pack = when {
                                    importUri != null -> {
                                        val stream = app.contentResolver.openInputStream(importUri!!)
                                            ?: throw LauncherException("Не удалось открыть файл", "файл недоступен")
                                        ModpackService.fetchLocal(stream) { importStage = it }
                                    }
                                    else -> {
                                        importStage = "Поиск модпака…"
                                        val direct = ModpackService.resolvePackUrl(importUrl)
                                        ModpackService.fetch(direct) { importStage = it }
                                    }
                                }
                                val idx = pack.index
                                val loader = ModpackService.loaderOf(idx)
                                val gameVersion = ModpackService.gameVersionOf(idx)
                                if (gameVersion.isBlank()) {
                                    pack.release()
                                    throw LauncherException("Пак не указывает версию игры", "нет minecraft в dependencies")
                                }
                                // лимит FREE — тот же, что в мастере
                                if (!app.billing.isPremiumActive() && app.instances.list().size >= 3) {
                                    pack.release()
                                    throw LauncherException(
                                        "Лимит сборок",
                                        "В бесплатной версии не более 3 сборок. Импорт можно делать, освободив место или с Premium.",
                                    )
                                }
                                val inst = app.instances.create(
                                    name = idx.name.ifBlank { "Модпак $gameVersion" },
                                    versionId = gameVersion,
                                    loader = loader,
                                    loaderVersion = idx.dependencies["fabric-loader"]
                                        ?: idx.dependencies["quilt-loader"],
                                )
                                ModpackService.installFiles(pack, Storage.instanceDir(app, inst.id)) { p ->
                                    importStage = "Копирование файлов…"
                                    importProgress = p
                                }
                                if (LoaderService.isSupported(loader)) {
                                    try {
                                        val lv = LoaderService.latestLoader(loader)
                                        LoaderService.installLoader(app, loader, gameVersion, lv.version) { s, d, t ->
                                            app.installProgress.value = InstallProgress(s, d, t)
                                        }
                                        app.logDownload("Загрузчик $loader ${lv.version}", inst.name, 0, true)
                                    } catch (_: Exception) {
                                    }
                                    app.installProgress.value = null
                                }
                                app.logDownload(inst.name, "Модпак → ${inst.versionId}", 0, true)
                                showImport = false
                                importStage = null
                                importProgress = null
                                importUrl = ""
                                importUri = null
                                importFileName = null
                                refresh()
                                nav.navigate("instance/${inst.id}")
                            } catch (e: Exception) {
                                importStage = null
                                importProgress = null
                                importError = e.message ?: "Ошибка импорта"
                            }
                        }
                    },
                ) { Text(if (importStage == null) "Импорт" else "Идёт импорт…", color = Acc) }
            },
            dismissButton = {
                TextButton(
                    enabled = importStage == null,
                    onClick = { showImport = false },
                ) { Text("Отмена", color = Mute) }
            },
        )
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = if (danger) Err else Mute, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = if (danger) Err else Mute)
    }
}
