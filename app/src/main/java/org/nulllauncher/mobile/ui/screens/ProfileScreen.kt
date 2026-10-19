package org.nulllauncher.mobile.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
    var themeMode by remember { mutableStateOf("light") }
    var showOffline by remember { mutableStateOf(false) }
    var offlineName by remember { mutableStateOf("") }
    var offlineError by remember { mutableStateOf<String?>(null) }

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
            themeMode = app.settings.currentThemeMode()
        } catch (_: Exception) {
            // дефолты уже стоят
        }
        try {
            storageText = app.versions.freeMemoryText()
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
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Line)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Офлайн-аккаунт — локальное имя для игры без авторизации. " +
                            "На таких серверах, где не проверяют лицензию.",
                        fontSize = 11.sp, color = Mute,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showOffline = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !waiting,
                    ) { Text("Офлайн-аккаунт", fontSize = 13.sp, color = Acc2) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(AccSoft),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, null, tint = Acc2, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (account!!.accessToken.isBlank()) "Офлайн · ${account!!.uuid.take(8)}…"
                                else "Microsoft · ${account!!.uuid.take(8)}…",
                                fontSize = 11.sp, color = Mute,
                            )
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
                    }) { Text("Выйти и удалить данные аккаунта", fontSize = 12.sp, color = Err) }
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

        // ---------- тема ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Тема оформления", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "Светлая", "dark" to "Тёмная", "system" to "Системная").forEach { (id, label) ->
                        val selected = themeMode == id
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AccSoft else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    themeMode = id
                                    scope.launch { app.settings.setThemeMode(id) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                color = if (selected) Acc2 else Mute,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- о приложении / логи ----------
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                TextButton(onClick = { nav.navigate("subscription") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Star, null, tint = Acc2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Премиум подписка", fontSize = 13.sp, color = Acc2)
                }
                HorizontalDivider(color = Line)
                TextButton(onClick = { nav.navigate("ai") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Acc2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ИИ-помощник", fontSize = 13.sp, color = Acc2)
                }
                HorizontalDivider(color = Line)
                TextButton(onClick = { nav.navigate("privacy") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Description, null, tint = Acc2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Конфиденциальность", fontSize = 13.sp, color = Acc2)
                }
                HorizontalDivider(color = Line)
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

    // ---------- диалог офлайн-аккаунта ----------
    if (showOffline) {
        AlertDialog(
            onDismissRequest = { showOffline = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Офлайн-аккаунт", fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "Придумай ник (3–16 символов: буквы, цифры, _). Без авторизации — для локальных миров " +
                            "и серверов, которые не проверяют лицензию.",
                        fontSize = 12.sp, color = Mute,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = offlineName,
                        onValueChange = { offlineName = it; offlineError = null },
                        singleLine = true,
                        label = { Text("Ник игрока") },
                        isError = offlineError != null,
                    )
                    offlineError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, fontSize = 11.sp, color = Err)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        account = app.accounts.addOffline(offlineName)
                        showOffline = false
                        offlineName = ""
                        offlineError = null
                    } catch (e: Exception) {
                        offlineError = e.message ?: "Не получилось создать аккаунт"
                    }
                }) { Text("Создать", color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { showOffline = false }) { Text("Отмена", color = Mute) }
            },
        )
    }
}

/**
 * Карточка Java Runtime: по строке на каждую версию (8 / 17 / 21 / 25).
 * Нужная версия ставится автоматически при запуске, здесь — ручная установка/переустановка.
 */
@Composable
private fun JavaRuntimeCard(app: NullApp) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<List<Int>>(emptyList()) }

    val busy by org.nulllauncher.mobile.data.runtime.JreInstaller.busyMajor.collectAsState()
    val stage by org.nulllauncher.mobile.data.runtime.JreInstaller.busyStage.collectAsState()
    val progress by org.nulllauncher.mobile.data.runtime.JreInstaller.busyProgress.collectAsState()
    val error by org.nulllauncher.mobile.data.runtime.JreInstaller.lastError.collectAsState()

    fun refresh() {
        installed = org.nulllauncher.mobile.data.runtime.JreInstaller.installedMajors(app)
    }
    LaunchedEffect(Unit) { refresh() }
    // когда установка завершилась — перечитать список установленных
    LaunchedEffect(busy) { if (busy == null) refresh() }

    fun install(major: Int) {
        org.nulllauncher.mobile.data.runtime.JreInstaller.installShared(app, major)
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("Java Runtime", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Нужная версия ставится сама при запуске игры. Здесь можно поставить или переустановить вручную.",
                fontSize = 11.sp, color = Mute,
            )
            Spacer(Modifier.height(8.dp))
            org.nulllauncher.mobile.data.runtime.JreInstaller.SUPPORTED.forEach { major ->
                val has = major in installed
                val hint = when (major) {
                    8 -> "Minecraft до 1.16"
                    17 -> "1.17 – 1.20.4"
                    21 -> "1.20.5 – 1.21.x"
                    else -> "26.x и новее"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (has) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = if (has) Ok else Mute, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Java $major", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(hint, fontSize = 10.5.sp, color = Mute)
                    }
                    OutlinedButton(
                        onClick = { install(major) },
                        enabled = busy == null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (busy == major) "Установка…" else if (has) "Переустановить" else "Установить",
                            fontSize = 11.5.sp, color = Acc2,
                        )
                    }
                }
            }

            if (busy != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stage, fontSize = 11.sp, color = Acc2, modifier = Modifier.weight(1f))
                    if (progress >= 0) Text("${(progress * 100).toInt()}%", fontSize = 11.sp, color = Acc2)
                }
                Spacer(Modifier.height(6.dp))
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 11.sp, color = Err, maxLines = 4)
            }
        }
    }
}
