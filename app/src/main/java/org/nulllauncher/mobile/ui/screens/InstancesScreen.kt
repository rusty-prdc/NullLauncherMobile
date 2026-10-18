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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.nulllauncher.mobile.InstallProgress
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.loaders.LoaderService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Сборки: создание, переименование, копия, удаление с подтверждением, память.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var instances by remember { mutableStateOf(app.instances.list()) }
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Instance?>(null) }
    var deleteTarget by remember { mutableStateOf<Instance?>(null) }
    var gameVersions by remember { mutableStateOf<List<String>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (gameVersions.isEmpty()) {
            gameVersions = app.versions.manifest().versions.filter { it.type == "release" }.map { it.id }
        }
    }
    fun refresh() { instances = app.instances.list() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Сборки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreate = true }) {
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

    // ---------- диалог создания ----------
    if (showCreate && gameVersions.isNotEmpty()) {
        var name by remember { mutableStateOf("") }
        var version by remember { mutableStateOf(gameVersions.first()) }
        var loader by remember { mutableStateOf("vanilla") }
        var loaderExpanded by remember { mutableStateOf(false) }
        var versionExpanded by remember { mutableStateOf(false) }
        var mem by remember { mutableFloatStateOf(2f) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Новая сборка") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = versionExpanded, onExpandedChange = { versionExpanded = it }) {
                        OutlinedTextField(
                            value = version, onValueChange = {}, readOnly = true,
                            label = { Text("Версия Minecraft") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = versionExpanded, onDismissRequest = { versionExpanded = false }) {
                            gameVersions.take(40).forEach { v ->
                                DropdownMenuItem(text = { Text(v) }, onClick = { version = v; versionExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = it }) {
                        OutlinedTextField(
                            value = when (loader) { "fabric" -> "Fabric"; "forge" -> "Forge"; "quilt" -> "Quilt"; else -> "Vanilla" },
                            onValueChange = {}, readOnly = true,
                            label = { Text("Загрузчик модов") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(loaderExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                            listOf("vanilla" to "Vanilla", "fabric" to "Fabric", "forge" to "Forge", "quilt" to "Quilt").forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { loader = id; loaderExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Память: ${mem.toInt()} ГБ", fontSize = 13.sp)
                    Slider(value = mem, onValueChange = { mem = it }, valueRange = 1f..4f, steps = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val created = app.instances.create(
                        name = name.ifBlank { "Сборка $version" },
                        versionId = version,
                        loader = loader,
                    ).let { created ->
                        app.instances.update(created.copy(memoryMb = mem.toInt() * 1024))
                    }
                    showCreate = false
                    refresh()
                    if (loader != "vanilla") {
                        scope.launch {
                            try {
                                val lv = LoaderService.latestLoader(loader)
                                LoaderService.installLoader(app, loader, version, lv.version) { s, d, t ->
                                    app.installProgress.value = InstallProgress(s, d, t)
                                }
                            } catch (e: Exception) {
                                app.lastError.value = e.message ?: "Ошибка установки загрузчика"
                            } finally {
                                app.installProgress.value = null
                            }
                        }
                    }
                }) { Text("Создать", color = Acc) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена", color = Mute) } },
        )
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
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = if (danger) Err else Mute, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = if (danger) Err else Mute)
    }
}
