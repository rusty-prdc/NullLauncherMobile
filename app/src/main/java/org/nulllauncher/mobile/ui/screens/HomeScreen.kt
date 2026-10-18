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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.launch.LaunchState
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.ErrSoft
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главная (как в ПК-лаунчере): «Продолжить игру» с выбранной сборкой,
 * переключатель сборок, список «Мои сборки» с кнопкой запуска у каждой.
 */
@Composable
fun HomeScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var instances by remember { mutableStateOf(app.instances.list()) }
    val account = app.currentAccount()
    val installStage by app.installProgress.collectAsState()
    val launcherState by app.launcher.state.collectAsState()
    val error by app.lastError.collectAsState()

    // выбранная сборка: последняя игранная (или первая), переживает переключение вкладок
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        instances = app.instances.list()
        val saved = app.settings.lastInstanceIdValue()
        selectedId = instances.firstOrNull { it.id == saved }?.id
            ?: instances.firstOrNull()?.id
    }

    val selected = instances.firstOrNull { it.id == selectedId } ?: instances.firstOrNull()
    val busy = launcherState is LaunchState.Running || launcherState is LaunchState.Preparing

    fun launch(inst: Instance) {
        val err = app.launcher.checkInstance(inst)
        if (err != null) {
            app.lastError.value = err
            return
        }
        app.lastError.value = null
        selectedId = inst.id
        scope.launch {
            app.settings.setLastInstance(inst.id)
            app.instances.markPlayed(inst.id)
            app.launcher.start(inst, account, app.settings.javaPathValue()) { l -> app.appendLog(l) }
            instances = app.instances.list()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ---------- шапка ----------
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(AccSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Person, null, tint = Acc2, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(account?.name ?: "Гость", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (account != null) "Microsoft · аккаунт активен" else "Войди в профиле",
                    fontSize = 11.sp, color = Mute,
                )
            }
            IconButton(onClick = { nav.navigate("profile") }) {
                Icon(Icons.Filled.Settings, "Профиль", tint = Mute)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- карточка запуска ----------
        if (selected == null) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Нет ни одной сборки", color = Warn, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { nav.navigate("versions") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Acc),
                    ) {
                        Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Установить Minecraft", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Выбери версию — потом создай из неё сборку", color = Mute, fontSize = 11.sp)
                }
            }
        } else {
            val inst = selected
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(AccSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                inst.name.firstOrNull()?.uppercase() ?: "S",
                                fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Acc2,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ПРОДОЛЖИТЬ ИГРУ", fontSize = 10.sp, color = Acc2, fontWeight = FontWeight.Bold)
                            Text(inst.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                        }
                        // переключение сборки
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, "Другая сборка", tint = Mute)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            instances.forEach { other ->
                                DropdownMenuItem(
                                    text = { Text(other.name, fontSize = 13.sp) },
                                    onClick = {
                                        selectedId = other.id
                                        scope.launch { app.settings.setLastInstance(other.id) }
                                        menu = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("${loaderLabel(inst)} ${inst.versionId}", primary = true)
                        Chip("${inst.memoryMb / 1024} ГБ")
                        if (inst.lastPlayedAt > 0) {
                            Chip(SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(Date(inst.lastPlayedAt)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { launch(inst) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(18.dp),
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
                                fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            )
                        }
                    }
                    if (launcherState is LaunchState.Running || launcherState is LaunchState.Preparing) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { app.launcher.stop() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (launcherState is LaunchState.Running) "Остановить игру" else "Отмена", color = Err)
                        }
                    }
                    (launcherState as? LaunchState.Preparing)?.let { st ->
                        Spacer(Modifier.height(8.dp))
                        Text(st.stage, fontSize = 11.5.sp, color = Acc2)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (inst.lastPlayedAt > 0) "Играли: " + SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(Date(inst.lastPlayedAt))
                            else "Ещё не запускалась",
                            color = Mute, fontSize = 11.sp,
                        )
                        Text("${app.modCount(inst.id)} модов", color = Mute, fontSize = 11.sp)
                    }
                    // быстрый переход в карточку сборки
                    Text(
                        "Открыть сборку ›",
                        color = Acc2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { nav.navigate("instance/${inst.id}") },
                    )
                }
            }
        }

        // ---------- прогресс установки ----------
        installStage?.let { st ->
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(st.stage, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        if (st.total > 0) Text("${st.done * 100 / st.total}%", color = Acc2, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
        }

        // ---------- ошибка ----------
        error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ErrSoft)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, color = Err, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { app.lastError.value = null }) {
                        Icon(Icons.Filled.Close, "Закрыть", tint = Mute, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Мои сборки", color = Mute, fontSize = 12.sp)
            Text(
                "Все ›",
                color = Acc2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { nav.navigate("instances") },
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(instances, key = { it.id }) { inst ->
                InstanceRow(
                    inst = inst,
                    active = inst.id == selected?.id,
                    busy = busy,
                    onOpen = {
                        selectedId = inst.id
                        scope.launch { app.settings.setLastInstance(inst.id) }
                        nav.navigate("instance/${inst.id}")
                    },
                    onPlay = { launch(inst) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun InstanceRow(
    inst: Instance,
    active: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) AccSoft else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(
                    if (active) Acc else MaterialTheme.colorScheme.surfaceVariant,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    inst.name.firstOrNull()?.uppercase() ?: "S",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = if (active) Color.White else Acc2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
                Text(
                    "${loaderLabel(inst)} ${inst.versionId} · ${inst.memoryMb / 1024} ГБ RAM · ${inst.coverInfo()}",
                    fontSize = 10.5.sp, color = Mute, maxLines = 1,
                )
            }
            IconButton(onClick = onPlay, enabled = !busy) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Играть: ${inst.name}",
                    tint = if (busy) Mute else Acc2,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun Instance.coverInfo(): String =
    if (lastPlayedAt > 0) "играли " + SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(lastPlayedAt))
    else "не запускалась"

@Composable
fun Chip(text: String, primary: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) AccSoft else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 11.5.sp, color = if (primary) Acc2 else Mute)
    }
}

fun loaderLabel(inst: Instance): String = when (inst.loader) {
    "fabric" -> "Fabric"
    "forge" -> "Forge"
    "neoforge" -> "NeoForge"
    "quilt" -> "Quilt"
    else -> "Vanilla"
}
