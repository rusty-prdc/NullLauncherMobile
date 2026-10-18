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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.nulllauncher.mobile.DownloadRecord
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Загрузки — журнал скачанных файлов, как в ПК-лаунчере:
 * фильтры (все/готово/ошибки), счётчики, очистка завершённых,
 * активный прогресс текущей установки.
 */
@Composable
fun DownloadsScreen(app: NullApp, nav: NavController) {
    val records by app.downloads.collectAsState()
    val active by app.installProgress.collectAsState()
    var filter by remember { mutableStateOf("all") } // all | ok | error

    LaunchedEffect(Unit) { app.loadDownloads() }

    val shown = records.filter {
        when (filter) {
            "ok" -> it.ok
            "error" -> !it.ok
            else -> true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Загрузки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                TextButton(onClick = {
                    app.downloads.value = emptyList()
                    try { java.io.File(app.filesDir, "downloads.json").writeText("") } catch (_: Throwable) {}
                }) {
                    Text("Очистить", fontSize = 12.sp, color = Mute)
                }
            }
            IconButton(onClick = { app.loadDownloads() }) {
                Icon(Icons.Filled.Refresh, "Обновить", tint = Mute, modifier = Modifier.size(18.dp))
            }
        }

        Text("Файлы, которые скачивает лаунчер", fontSize = 11.sp, color = Mute)

        Spacer(Modifier.height(10.dp))

        // ---------- фильтры и счётчики ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip("Все", filter == "all") { filter = "all" }
            FilterChip("Готово", filter == "ok") { filter = "ok" }
            FilterChip("Ошибки", filter == "error") { filter = "error" }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Активных: ${if (active != null) 1 else 0} · Всего: ${records.size}",
            fontSize = 11.sp, color = Mute,
        )

        Spacer(Modifier.height(10.dp))

        // ---------- активная загрузка ----------
        active?.let { st ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AccSoft)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, null, tint = Acc2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(st.stage, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        if (st.total > 0) {
                            Text("${st.done * 100 / st.total}%", color = Acc2, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (shown.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Download, null, tint = Card2, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Пока пусто", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Здесь появятся скачанные версии, моды и ресурс-паки.",
                        fontSize = 11.5.sp, color = Mute,
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.at.toString() + it.name }) { rec ->
                DownloadRow(rec)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DownloadRow(rec: DownloadRecord) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Download,
                    null,
                    tint = if (rec.ok) Acc2 else Err,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(rec.name, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (rec.ok) "Готово" else "Ошибка",
                    fontSize = 10.sp,
                    color = if (rec.ok) Ok else Err,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rec.detail, fontSize = 10.5.sp, color = Mute, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        if (rec.size > 0) append(fmtSize(rec.size)).append(" · ")
                        append(SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(rec.at)))
                    },
                    fontSize = 10.sp, color = Mute,
                )
            }
        }
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> "%.0f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
