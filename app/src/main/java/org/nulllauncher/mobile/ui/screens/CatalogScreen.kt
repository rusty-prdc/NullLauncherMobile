package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import org.nulllauncher.mobile.data.modrinth.ModHit
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.ErrSoft
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok

/**
 * Каталог Modrinth: поиск, выбор целевой сборки, фильтры типа,
 * честная проверка совместимости (версия MC + загрузчик).
 */
@Composable
fun CatalogScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("mod") } // mod | modpack | resourcepack
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installingSlug by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf<InstallProgress?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var instances by remember { mutableStateOf(app.instances.list()) }
    var targetId by remember {
        mutableStateOf(app.instances.list().firstOrNull()?.id)
    }
    val target: Instance? = instances.firstOrNull { it.id == targetId } ?: instances.firstOrNull()

    fun refreshInstalled() {
        installed = if (target != null) {
            Storage.instanceMods(app, target.id)
                .listFiles()
                ?.map { it.name.removeSuffix(".disabled") }
                ?.map { it.substringBeforeLast("-") }  // slug-догадка по имени файла
                ?.toSet() ?: emptySet()
        } else emptySet()
    }

    fun doSearch() {
        scope.launch {
            searching = true
            message = null
            try {
                val res = ModrinthService.search(
                    query = query,
                    gameVersion = target?.versionId,
                    loader = target?.loader?.takeIf { it != "vanilla" },
                    projectType = type,
                )
                hits = res.hits
                refreshInstalled()
            } catch (e: Exception) {
                message = e.message ?: "Ошибка поиска"
            }
            searching = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Каталог", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Modrinth", fontSize = 11.sp, color = Mute)
        }

        Spacer(Modifier.height(10.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск модов…", color = Mute) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Mute, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(Modifier.height(10.dp))

        // ---------- тип проекта ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Моды", type == "mod") { type = "mod" }
            FilterChip("Модпаки", type == "modpack") { type = "modpack" }
            FilterChip("Ресурс-паки", type == "resourcepack") { type = "resourcepack" }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- выбор целевой сборки ----------
        if (target == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(ErrSoft)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Warning, null, tint = Err, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Нет сборок для установки модов",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Err,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сначала создай сборку в разделе «Сборки»",
                        fontSize = 11.sp,
                        color = Mute,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { nav.navigate("create_instance") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Acc),
            ) {
                Text("Создать коллекцию", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        } else {
            InstancePicker(
                instances = instances,
                selected = target,
                onSelect = {
                    targetId = it?.id
                    refreshInstalled()
                },
            )

            // ---------- совместимость ----------
            val compat = compatibilityNote(target, type)
            Spacer(Modifier.height(8.dp))
            if (compat == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Extension, null, tint = Ok, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Совместимо: ${target!!.versionId} · ${loaderLabel(target)}",
                        fontSize = 11.5.sp, color = Ok,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Err, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(compat, fontSize = 11.5.sp, color = Err, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { doSearch() }, modifier = Modifier.fillMaxWidth()) {
            Text("Найти", fontSize = 13.sp, color = Acc2, fontWeight = FontWeight.SemiBold)
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Err, fontSize = 12.sp)
        }

        if (searching) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(hits, key = { it.projectId }) { hit ->
                val compatNote = target?.let { t -> compatibilityNote(t, type, hit) }
                val incompatible = compatNote != null

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (incompatible) ErrSoft else MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hit.iconUrl != null) {
                            RemoteImage(
                                url = hit.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
                            )
                        } else {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Card2),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Extension, null, tint = Acc2, modifier = Modifier.size(22.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hit.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
                            Text(
                                "от ${hit.author} · ${fmtDownloads(hit.downloads)}",
                                fontSize = 11.sp, color = Mute,
                            )
                            compatNote?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, fontSize = 10.5.sp, color = Err, maxLines = 2)
                            }
                            if (installingSlug == hit.slug && installProgress != null) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (installProgress!!.total > 0)
                                            (installProgress!!.done.toFloat() / installProgress!!.total).coerceIn(0f, 1f) else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                                )
                            }
                        }
                        val isInstalled = installed.contains(hit.slug)
                        OutlinedButton(
                            enabled = !incompatible && installingSlug == null,
                            onClick = {
                                val t = target ?: run {
                                    message = "Сначала выбери или создай сборку"
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    installingSlug = hit.slug
                                    try {
                                        val ver = ModrinthService.bestVersion(hit.slug, t.versionId, t.loader)
                                            ?: throw LauncherException("Нет версии для ${t.versionId} · ${loaderLabel(t)}", hit.title)
                                        val file = ver.files.firstOrNull { f -> f.primary } ?: ver.files.firstOrNull()
                                            ?: throw LauncherException("В версии нет файла", ver.name)
                                        val dest = when (type) {
                                            "resourcepack" -> Storage.instanceResourcePacks(app, t.id)
                                            else -> Storage.instanceMods(app, t.id)
                                        }
                                        ModrinthService.installFile(file, dest) { d, tg ->
                                            installProgress = InstallProgress("Скачивание ${file.filename}", d, tg)
                                        }
                                        refreshInstalled()
                                    } catch (e: Exception) {
                                        message = e.message ?: "Ошибка установки"
                                    }
                                    installingSlug = null
                                    installProgress = null
                                }
                            },
                        ) {
                            Text(
                                when {
                                    isInstalled -> "Установлен"
                                    incompatible -> "Несовместимо"
                                    else -> "Установить"
                                },
                                fontSize = 11.5.sp,
                                color = when {
                                    isInstalled -> Ok
                                    incompatible -> Err
                                    else -> Acc2
                                },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/**
 * Проверка совместимости цели (null = совместимо).
 * - ваниль не грузит моды вообще;
 * - модпаки в ваниль тоже нельзя;
 * - ресурс-паки можно в любую сборку;
 * - моды требуют версию MC и загрузчик из списка проекта.
 */
private fun compatibilityNote(t: Instance, type: String, hit: ModHit? = null): String? {
    if (type == "resourcepack") return null
    if (t.loader == "vanilla") {
        return if (type == "modpack") "Модпак нельзя запустить на Vanilla — нужна сборка с Fabric/Forge/NeoForge/Quilt"
        else "Моды не работают на Vanilla — выбери сборку с Fabric/Forge/Quilt"
    }
    if (hit != null) {
        val versions = hit.versions
        if (versions.isNotEmpty() && t.versionId !in versions) {
            return "Не выходит на ${t.versionId} (поддерживает: ${versions.take(3).joinToString(", ")}${if (versions.size > 3) "…" else ""})"
        }
        val cats = hit.categories
        if (cats.isNotEmpty()) {
            val loaderCat = when (t.loader) {
                "fabric" -> "fabric"
                "forge" -> "forge"
                "neoforge" -> "neoforge"
                "quilt" -> "quilt"
                else -> null
            }
            val hasAnyLoader = listOf("fabric", "forge", "neoforge", "quilt", "liteloader").any { it in cats }
            if (loaderCat != null && hasAnyLoader && loaderCat !in cats) {
                return "Для ${loaderLabel(t)}, а мод под ${cats.filter { it in listOf("fabric", "forge", "neoforge", "quilt") }.joinToString("/")}"
            }
        }
    }
    return null
}

/** Выпадающий выбор целевой сборки */
@Composable
private fun InstancePicker(
    instances: List<Instance>,
    selected: Instance?,
    onSelect: (Instance?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = instances.isNotEmpty() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Установить в:", fontSize = 10.5.sp, color = Mute)
            Text(
                selected?.let { "${it.name} · ${it.versionId} · ${loaderLabel(it)}" } ?: "нет сборок — создай в разделе «Сборки»",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected == null) Err else MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            instances.forEach { inst ->
                DropdownMenuItem(
                    text = { Text("${inst.name} · ${inst.versionId} · ${loaderLabel(inst)}", fontSize = 13.sp) },
                    onClick = { onSelect(inst); expanded = false },
                )
            }
        }
        Text("▾", color = Mute, fontSize = 14.sp)
    }
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(1.dp, Acc2, RoundedCornerShape(999.dp))
                else Modifier.border(1.dp, Line, RoundedCornerShape(999.dp)),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Acc2 else Mute,
        )
    }
}

private fun fmtDownloads(n: Long): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс".format(n / 1_000.0)
    else -> "$n"
}
