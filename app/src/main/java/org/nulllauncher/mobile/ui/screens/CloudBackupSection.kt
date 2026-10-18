package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.cloud.CloudBackupInfo
import org.nulllauncher.mobile.data.cloud.CloudBackupService
import org.nulllauncher.mobile.data.cloud.YandexDisk
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Облачные бэкапы (Premium): ZIP папки сборки → Яндекс Диск.
 * Подключение: пользователь вставляет токен OAuth с правами Disk.
 */
@Composable
fun CloudBackupSection(app: NullApp, instanceId: String) {
    val scope = rememberCoroutineScope()
    val premium = app.billing.isPremiumActive()

    var token by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<CloudBackupInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showConnect by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<CloudBackupInfo?>(null) }
    var pendingDelete by remember { mutableStateOf<CloudBackupInfo?>(null) }

    LaunchedEffect(Unit) { token = app.settings.currentYandexToken() }

    fun refresh() {
        val t = token ?: return
        scope.launch {
            try {
                backups = CloudBackupService.list(YandexDisk(t), instanceId)
            } catch (e: Exception) {
                status = e.message ?: "Не удалось получить список"
            }
        }
    }

    LaunchedEffect(token) { if (token != null) refresh() }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudUpload, null, tint = Acc2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Облачные бэкапы", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (token != null) Text("${backups.size}", fontSize = 12.sp, color = Mute)
            }

            if (!premium) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = Mute, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Premium-функция: сохраняй сборки в облако и восстанавливай на любом устройстве.",
                        fontSize = 11.5.sp, color = Mute, modifier = Modifier.weight(1f),
                    )
                }
                return@Column
            }

            if (token == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Свяжи Яндекс Диск — бэкапы будут храниться в твоём облаке.",
                    fontSize = 11.5.sp, color = Mute,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showConnect = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) { Text("Подключить Яндекс Диск", fontSize = 13.sp, color = MaterialTheme.colorScheme.surface) }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            status = "Создание архива…"
                            scope.launch {
                                try {
                                    val disk = YandexDisk(token!!)
                                    val b = CloudBackupService.create(app, instanceId, disk)
                                    status = "Загружено: ${fmtSize(b.size)}"
                                    refresh()
                                } catch (e: Exception) {
                                    status = e.message ?: "Ошибка бэкапа"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Acc),
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Text(if (busy) "Загрузка…" else "Создать бэкап", fontSize = 13.sp, color = MaterialTheme.colorScheme.surface)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showConnect = true }) {
                        Text("Сменить токен", fontSize = 12.sp, color = Mute)
                    }
                }

                status?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 11.sp, color = Acc2, maxLines = 2)
                }

                if (backups.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Line)
                    backups.forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(b.modifiedAt)),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${fmtSize(b.size)} · ${b.name.substringAfterLast('_')}",
                                    fontSize = 10.sp, color = Mute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { pendingRestore = b }, enabled = !busy) {
                                Text("Восстановить", fontSize = 11.5.sp, color = Acc2)
                            }
                            IconButton(onClick = { pendingDelete = b }, enabled = !busy) {
                                Icon(Icons.Filled.Delete, "Удалить бэкап", tint = Mute, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("Бэкапов пока нет — создай первый.", fontSize = 11.sp, color = Mute)
                }
            }
        }
    }

    // ---------- подключение токена ----------
    if (showConnect) {
        var input by remember { mutableStateOf(token ?: "") }
        var checking by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showConnect = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Яндекс Диск", fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "1. Открой oauth.yandex.ru и создай приложение (или возьми токен на странице выдачи).\n" +
                            "2. Выдай токену права Disk (чтение и запись).\n" +
                            "3. Вставь токен ниже.",
                        fontSize = 11.5.sp, color = Mute,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text("Токен OAuth") },
                        isError = err != null,
                    )
                    err?.let { Spacer(Modifier.height(4.dp)); Text(it, fontSize = 11.sp, color = Err) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (input.isBlank()) { err = "Вставь токен"; return@TextButton }
                    checking = true
                    err = null
                    scope.launch {
                        val ok = YandexDisk(input.trim()).ping()
                        if (ok) {
                            app.settings.setYandexToken(input.trim())
                            token = input.trim()
                            showConnect = false
                            status = null
                        } else {
                            err = "Токен не прошёл проверку — проверь права Disk"
                        }
                        checking = false
                    }
                }) { Text(if (checking) "Проверка…" else "Подключить", color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { showConnect = false }) { Text("Отмена", color = Mute) }
            },
        )
    }

    // ---------- подтверждение восстановления ----------
    pendingRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Восстановить бэкап?", fontSize = 15.sp) },
            text = {
                Text(
                    "Файлы сборки будут заменены данными из архива от " +
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(b.modifiedAt)) + ".",
                    fontSize = 12.5.sp, color = Mute,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    status = "Загрузка и распаковка…"
                    scope.launch {
                        try {
                            CloudBackupService.restore(app, b, YandexDisk(token!!))
                            status = "Бэкап восстановлен"
                        } catch (e: Exception) {
                            status = e.message ?: "Ошибка восстановления"
                        }
                        busy = false
                    }
                }) { Text("Восстановить", color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Отмена", color = Mute) }
            },
        )
    }

    // ---------- подтверждение удаления ----------
    pendingDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить бэкап?", fontSize = 15.sp) },
            text = { Text(b.name, fontSize = 12.sp, color = Mute) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        try {
                            CloudBackupService.delete(YandexDisk(token!!), b)
                            refresh()
                        } catch (e: Exception) {
                            status = e.message ?: "Ошибка удаления"
                        }
                    }
                }) { Text("Удалить", color = Err) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена", color = Mute) }
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
