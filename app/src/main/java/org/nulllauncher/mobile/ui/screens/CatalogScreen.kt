package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
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
import io.coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.InstallProgress
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.modrinth.ModHit
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok

/**
 * Каталог Modrinth: поиск, фильтры, установка модов в выбранную сборку.
 */
@Composable
fun CatalogScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installingSlug by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf<InstallProgress?>(null) }

    val instances = remember { app.instances.list() }
    val target = instances.firstOrNull()

    fun doSearch() {
        scope.launch {
            searching = true
            try {
                val res = ModrinthService.search(query)
                hits = res.hits
                installed = if (target != null) {
                    ModrinthService.installedMods(
                        org.nulllauncher.mobile.core.Storage.instanceMods(app, target.id)
                    ).map { it.name.removeSuffix(".disabled").substringBeforeLast("-") }.toSet()
                } else emptySet()
            } catch (e: Exception) {
                app.lastError.value = e.message ?: "Ошибка поиска"
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
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Card, unfocusedContainerColor = Card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Установка в: ${target?.name ?: "нет сборок"}", fontSize = 11.5.sp, color = Mute)
            OutlinedButton(onClick = { doSearch() }) { Text("Найти", fontSize = 12.sp, color = Color(0xFFC4B0FF)) }
        }

        if (searching) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(hits, key = { it.projectId }) { hit ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hit.iconUrl != null) {
                            AsyncImage(
                                model = hit.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Card2),
                            )
                        } else {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Card2),
                                contentAlignment = Alignment.Center,
                            ) { Text("🧩", fontSize = 22.sp) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hit.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(
                                "от ${hit.author} · ${fmtDownloads(hit.downloads)}",
                                fontSize = 11.sp, color = Mute,
                            )
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
                        val gameVersion = target?.versionId
                        val loader = target?.loader ?: "fabric"
                        OutlinedButton(onClick = {
                            if (target == null) {
                                app.lastError.value = "Создай сборку для установки модов"
                                return@OutlinedButton
                            }
                            scope.launch {
                                installingSlug = hit.slug
                                try {
                                    val ver = ModrinthService.bestVersion(hit.slug, gameVersion!!, loader)
                                        ?: throw org.nulllauncher.mobile.core.LauncherException("Нет совместимой версии", hit.title)
                                    val file = ver.files.firstOrNull { it.primary } ?: ver.files.firstOrNull()
                                        ?: throw org.nulllauncher.mobile.core.LauncherException("В версии нет файла", ver.name)
                                    ModrinthService.installFile(
                                        file,
                                        org.nulllauncher.mobile.core.Storage.instanceMods(app, target.id),
                                    ) { d, t -> installProgress = InstallProgress("Скачивание ${file.filename}", d, t) }
                                    app.instances.markPlayed(target.id)
                                } catch (e: Exception) {
                                    app.lastError.value = e.message ?: "Ошибка установки"
                                }
                                installingSlug = null
                                installProgress = null
                            }
                        }) {
                            Text(
                                if (installed.contains(hit.slug)) "Установлен" else "Установить",
                                fontSize = 11.5.sp,
                                color = if (installed.contains(hit.slug)) Ok else Color(0xFFC4B0FF),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmtDownloads(n: Long): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс".format(n / 1_000.0)
    else -> "$n"
}
