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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.versions.ManifestVersion
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import org.nulllauncher.mobile.ui.theme.Warn
import org.nulllauncher.mobile.ui.theme.Line

/**
 * Менеджер версий: официальный манифест Mojang, фильтры релизы/снапшоты,
 * установка с прогрессом и отменой.
 */
@Composable
fun VersionsScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<ManifestVersion>>(emptyList()) }
    var filter by remember { mutableStateOf("release") }
    var query by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }

    val install by app.installProgress.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        versions = app.versions.manifest().versions
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Text("Версии", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(10.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск версии…", color = Mute) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Релизы", filter == "release") { filter = "release" }
            FilterChip("Снапшоты", filter == "snapshot") { filter = "snapshot" }
        }

        Spacer(Modifier.height(10.dp))

        val shown = versions
            .filter { it.type == filter }
            .filter { query.isBlank() || it.id.contains(query, ignoreCase = true) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(shown) { v ->
                val installed = app.versions.isInstalled(v.id)
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Card2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (v.type == "release") Icons.Filled.CheckCircle else Icons.Filled.Science,
                                    null,
                                    tint = if (v.type == "release") Ok else Warn,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.id, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text(
                                    (if (v.type == "release") "Релиз" else "Снапшот") +
                                        (if (v.releaseTime.isNotBlank()) " · " + v.releaseTime.take(10) else ""),
                                    fontSize = 11.sp, color = Mute,
                                )
                                if (installing == v.id && install != null) {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { if (install!!.total > 0) (install!!.done.toFloat() / install!!.total).coerceIn(0f, 1f) else 0f },
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(install!!.stage, fontSize = 10.5.sp, color = Acc2)
                                        OutlinedButton(onClick = {
                                            org.nulllauncher.mobile.core.InstallCancel.requested = true
                                            installing = null
                                            app.installProgress.value = null
                                        }) {
                                            Text("Отмена", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            if (installed) {
                                Chip("Установлена", primary = false)
                            } else if (installing != v.id) {
                                OutlinedButton(onClick = {
                                    installing = v.id
                                    org.nulllauncher.mobile.core.InstallCancel.begin()
                                    scope.launch {
                                        try {
                                            app.versions.install(v.id) { stage, done, total ->
                                                app.installProgress.value =
                                                    org.nulllauncher.mobile.InstallProgress(stage, done, total)
                                            }
                                            versions = app.versions.manifest().versions
                                        } catch (e: Exception) {
                                            app.lastError.value = e.message ?: "Ошибка установки"
                                        } finally {
                                            installing = null
                                            app.installProgress.value = null
                                        }
                                    }
                                }) {
                                    Text("Установить", fontSize = 12.sp, color = Acc2)
                                }
                            }
                        }
                        // установленная версия → сразу создать из неё сборку и играть
                        if (installed && installing == null) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val existing = app.instances.list().firstOrNull {
                                        it.versionId == v.id && it.loader == "vanilla"
                                    }
                                    val created = existing ?: app.instances.create(
                                        name = "Сборка $v.id",
                                        versionId = v.id,
                                    )
                                    scope.launch { app.settings.setLastInstance(created.id) }
                                    nav.navigate("instance/${created.id}")
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Acc),
                            ) {
                                Text(
                                    if (app.instances.list().any { it.versionId == v.id }) "Открыть сборку $v.id" else "Создать сборку $v.id",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

