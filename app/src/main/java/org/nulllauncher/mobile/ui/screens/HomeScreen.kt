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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.ErrSoft
import org.nulllauncher.mobile.ui.theme.Line

@Composable
fun HomeScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var instances by remember { mutableStateOf(app.instances.list()) }
    LaunchedEffect(Unit) { instances = app.instances.list() }

    val account = app.currentAccount()
    val installStage by app.installProgress.collectAsState()
    val launcherState by app.launcher.state.collectAsState()
    val error by app.lastError.collectAsState()

    val selected = instances.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ---------- шапка ----------
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Card2),
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
                        Text("Minecraft не установлен", color = Warn, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { nav.navigate("versions") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccSoft),
                    ) {
                        Icon(Icons.Filled.Download, null, tint = Acc2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Установить Minecraft", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Acc2)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Выбери версию в менеджере", color = Mute, fontSize = 11.sp)
                }
            }
        } else {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("${loaderLabel(selected)} ${selected.versionId}", primary = true)
                        Chip("${selected.memoryMb / 1024} ГБ")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val err = app.launcher.checkInstance(selected)
                            if (err != null) {
                                app.lastError.value = err
                            } else {
                                app.lastError.value = null
                                scope.launch {
                                    app.launcher.start(selected, account, app.settings.javaPathValue()) { l ->
                                        app.appendLog(l)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Acc),
                        enabled = launcherState !is LaunchState.Running && launcherState !is LaunchState.Preparing,
                    ) {
                        if (launcherState !is LaunchState.Preparing && launcherState !is LaunchState.Running) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
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
                    if (launcherState is LaunchState.Running) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { app.launcher.stop() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Остановить игру")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val lp = selected.lastPlayedAt
                        Text(
                            if (lp > 0) "Запуск: " + SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(Date(lp))
                            else "Ещё не запускалась",
                            color = Mute, fontSize = 11.sp,
                        )
                        Text("${app.modCount(selected.id)} модов", color = Mute, fontSize = 11.sp)
                    }
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
        Text("Мои сборки", color = Mute, fontSize = 12.sp)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(instances, key = { it.id }) { inst ->
                InstanceRow(inst) { nav.navigate("instance/${inst.id}") }
            }
        }
    }
}

@Composable
private fun InstanceRow(inst: Instance, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(AccSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    inst.name.firstOrNull()?.uppercase() ?: "S",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Acc2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(
                    "${loaderLabel(inst)} ${inst.versionId} · ${inst.memoryMb / 1024} ГБ RAM",
                    fontSize = 11.sp, color = Mute,
                )
            }
            Text("›", color = Mute, fontSize = 18.sp)
        }
    }
}

@Composable
fun Chip(text: String, primary: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) Acc.copy(alpha = .2f) else Card2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 11.5.sp, color = if (primary) Acc2 else MaterialTheme.colorScheme.onBackground)
    }
}

fun loaderLabel(inst: Instance): String = when (inst.loader) {
    "fabric" -> "Fabric"
    "forge" -> "Forge"
    "neoforge" -> "NeoForge"
    "quilt" -> "Quilt"
    else -> "Vanilla"
}
