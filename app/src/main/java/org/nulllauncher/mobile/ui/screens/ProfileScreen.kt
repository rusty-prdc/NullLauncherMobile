package org.nulllauncher.mobile.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.BuildConfig
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.accounts.AccountService
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import org.nulllauncher.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Line

/**
 * Профиль: аккаунты Microsoft (device-code вход) + настройки производительности.
 */
@Composable
fun ProfileScreen(app: NullApp, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf(app.currentAccount()) }
    var deviceCode by remember { mutableStateOf<AccountService.DeviceCode?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // Безопасные значения по умолчанию — до загрузки из DataStore
    var memoryMb by remember { mutableFloatStateOf(2048f) }
    var maxMemory by remember { mutableStateOf(4096) }
    var minMemory by remember { mutableStateOf(512) }
    var fullscreen by remember { mutableStateOf(false) }
    var touchControls by remember { mutableStateOf(true) }
    var renderScale by remember { mutableFloatStateOf(100f) }
    var guiScale by remember { mutableStateOf(0) }
    var storageText by remember { mutableStateOf("") }
    var versionsCount by remember { mutableStateOf(0) }
    var instancesCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            minMemory = app.settings.minMemoryMb()
            maxMemory = app.settings.maxMemoryMb()
            memoryMb = app.settings.memoryMb.first().toFloat().coerceIn(minMemory.toFloat(), maxMemory.toFloat())
            fullscreen = app.settings.fullscreen.first()
            touchControls = app.settings.touchControls.first()
            renderScale = app.settings.renderScale.first().toFloat()
            guiScale = app.settings.guiScale.first()
        } catch (_: Exception) {
            // дефолты уже стоят
        }
        try {
            storageText = app.versions.freeSpaceText()
            versionsCount = app.versionsCount()
            instancesCount = app.instances.list().size
        } catch (_: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Профиль", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))

        Spacer(Modifier.height(10.dp))

        // ---------- аккаунт ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                if (account == null) {
                    Text("Вход через Microsoft", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Официальный вход: твой аккаунт Minecraft, без паролей в приложении.",
                        fontSize = 12.sp, color = Mute,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                waiting = true
                                loginError = null
                                try {
                                    val dc = app.accounts.requestDeviceCode()
                                    deviceCode = dc
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))
                                    context.startActivity(intent)
                                    val ms = app.accounts.pollToken(dc.deviceCode)
                                    val acc = app.accounts.loginWithMicrosoft(ms)
                                    app.accounts.add(acc)
                                    account = acc
                                } catch (e: Exception) {
                                    loginError = e.message ?: "Ошибка входа"
                                }
                                waiting = false
                                deviceCode = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !waiting,
                        colors = ButtonDefaults.buttonColors(containerColor = Acc),
                    ) { Text("Войти через Microsoft", fontWeight = FontWeight.SemiBold) }

                    if (waiting && deviceCode != null) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text("Код: ${deviceCode!!.userCode}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Acc)
                        Text("Введи его на ${deviceCode!!.verificationUri}", fontSize = 12.sp, color = Mute)
                    }
                    loginError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Err, fontSize = 12.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(AccSoft),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, null, tint = Acc2, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Microsoft · ${account!!.uuid.take(8)}…", fontSize = 11.sp, color = Mute)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    account = app.accounts.refresh(account!!)
                                } catch (e: Exception) {
                                    loginError = "Токен не обновился: ${e.message}"
                                }
                            }
                        }) { Text("Обновить", fontSize = 12.sp, color = Ok) }
                    }
                    loginError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Err, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        account?.let { acc -> app.accounts.remove(acc.uuid) }
                        account = null
                    }) { Text("Выйти из аккаунта", fontSize = 12.sp, color = Err) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- память ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Оперативная память", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("${"%.1f".format(memoryMb / 1024f)} ГБ", color = Acc2, fontSize = 13.5.sp)
                }
                Slider(
                    value = memoryMb.coerceIn(minMemory.toFloat(), maxMemory.toFloat()),
                    onValueChange = { memoryMb = it },
                    onValueChangeFinished = {
                        scope.launch { app.settings.setMemoryMb(memoryMb.toInt()) }
                    },
                    valueRange = minMemory.toFloat()..maxMemory.toFloat(),
                )
                Text(
                    "макс. безопасный: ${"%.1f".format(maxMemory / 1024f)} ГБ — больше нельзя без риска сбоя",
                    fontSize = 11.sp, color = Mute,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- графика ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Полноэкранный режим", fontSize = 13.5.sp)
                        Text("Без системных панелей", fontSize = 11.sp, color = Mute)
                    }
                    Switch(
                        checked = fullscreen,
                        onCheckedChange = { fullscreen = it; scope.launch { app.settings.setFullscreen(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Acc),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Сенсорное управление", fontSize = 13.5.sp)
                        Text("джойстик + кнопки", fontSize = 11.sp, color = Mute)
                    }
                    Switch(
                        checked = touchControls,
                        onCheckedChange = { touchControls = it; scope.launch { app.settings.setTouchControls(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Acc),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Разрешение рендера", fontSize = 13.5.sp)
                    Text("${renderScale.toInt()}%", color = Acc2, fontSize = 13.5.sp)
                }
                Slider(
                    value = renderScale,
                    onValueChange = { renderScale = it },
                    onValueChangeFinished = { scope.launch { app.settings.setRenderScale(renderScale.toInt()) } },
                    valueRange = 50f..150f,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Размер GUI", fontSize = 13.5.sp)
                    Text(if (guiScale == 0) "Авто" else "$guiScale", color = Acc2, fontSize = 13.5.sp)
                }
                Slider(
                    value = guiScale.toFloat(),
                    onValueChange = { guiScale = it.toInt() },
                    onValueChangeFinished = { scope.launch { app.settings.setGuiScale(guiScale) } },
                    valueRange = 0f..4f,
                    steps = 3,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- Java Runtime ----------
        JavaRuntimeCard(app)

        Spacer(Modifier.height(14.dp))

        // ---------- место и статистика ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Хранилище", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(storageText.ifBlank { "—" }, fontSize = 12.sp, color = Mute)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Версий Minecraft: $versionsCount", fontSize = 12.sp, color = Mute)
                    Text("Сборок: $instancesCount", fontSize = 12.sp, color = Mute)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- о приложении / логи ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                TextButton(onClick = { nav.navigate("logs") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Description, null, tint = Acc2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Логи запуска", fontSize = 13.sp, color = Acc2)
                }
                HorizontalDivider(color = Line)
                Column(Modifier.padding(top = 10.dp)) {
                    Text("nullLauncher Mobile ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Вход через Microsoft — официально, без паролей. Для запуска игры нужно Java-окружение (см. выше). Minecraft — товар Mojang/Microsoft.",
                        fontSize = 11.sp, color = Mute,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Карточка Java Runtime: авто-установка одной кнопкой —
 * качаем OpenJDK 17 для Android из публичных сборок
 * и распаковываем в runtimes/. Прогресс прямо в карточке.
 */
@Composable
private fun JavaRuntimeCard(app: NullApp) {
    val scope = rememberCoroutineScope()
    var javaPath by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) } // -1 = неизвестный размер
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        javaPath = app.settings.currentJavaPath()
            ?: org.nulllauncher.mobile.data.runtime.JreInstaller.installedJava(app)
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("Java Runtime", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(4.dp))
            if (javaPath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Ok, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Установлена (OpenJDK 17 для Android)",
                        fontSize = 11.5.sp, color = Ok, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(javaPath!!, fontSize = 10.sp, color = Mute, maxLines = 1)
            } else if (!installing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Не установлена. Нужна для запуска игры — ставится автоматически.",
                        fontSize = 11.sp, color = Warn, modifier = Modifier.weight(1f),
                    )
                }
            }

            if (installing) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stage, fontSize = 11.sp, color = Acc2, modifier = Modifier.weight(1f))
                    if (progress >= 0) Text("${(progress * 100).toInt()}%", fontSize = 11.sp, color = Acc2)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                )
            }

            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 11.sp, color = Err, maxLines = 3)
            }

            Spacer(Modifier.height(10.dp))
            if (javaPath == null) {
                Button(
                    onClick = {
                        if (installing) return@Button
                        scope.launch {
                            installing = true
                            error = null
                            try {
                                val path = org.nulllauncher.mobile.data.runtime.JreInstaller.install(app, 17) { st, done, total ->
                                    stage = st
                                    progress = if (total > 0) done.toFloat() / total else -1f
                                }
                                app.settings.setJavaPath(path)
                                javaPath = path
                            } catch (e: Exception) {
                                error = e.message ?: "Не удалось скачать Java"
                            }
                            installing = false
                        }
                    },
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                ) {
                    if (installing) {
                        Text("Установка…", fontSize = 13.sp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Установить Java 17 (автоматически)", fontSize = 13.sp, color = Color.White)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                org.nulllauncher.mobile.data.runtime.JreInstaller.install(app, 17).let {
                                    app.settings.setJavaPath(it)
                                    javaPath = it
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "Ошибка переустановки"
                            }
                        }
                    },
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Переустановить", fontSize = 12.sp, color = Acc2)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Источник: публичные сборки OpenJDK для Android (aarch64/armeabi/x86_64). Архив .tar.xz распаковывается в runtimes/ автоматически.",
                fontSize = 10.5.sp, color = Mute,
            )
        }
    }
}
