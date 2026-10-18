package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.ui.theme.Mute
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.Txt
import org.nulllauncher.mobile.ui.theme.Acc

/**
 * Логи запусков: список файлов + просмотр с копированием/очисткой.
 */
@Composable
fun LogsScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(listOf<File>()) }
    var openFile by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        files = Storage.logs(app).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Text("Логи запуска", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (files.isNotEmpty()) {
                IconButton(onClick = {
                    scope.launch {
                        files.forEach { it.delete() }
                        refresh()
                    }
                }) {
                    Icon(Icons.Filled.Delete, "Очистить все", tint = Mute)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (files.isEmpty()) {
            Text("Пока пусто. Логи появятся после первого запуска игры.", color = Mute, fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.absolutePath }) { f ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { openFile = f },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                Text(
                                    "${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))} · ${f.length() / 1024} КБ",
                                    fontSize = 11.sp, color = Mute,
                                )
                            }
                            Text("›", color = Mute, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    openFile?.let { f ->
        val text = remember(f) {
            try {
                f.readText().takeLast(20000)
            } catch (e: Exception) {
                "Не удалось прочитать: ${e.message}"
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { openFile = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(f.name, fontSize = 14.sp) },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Txt,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { openFile = null }) {
                    Text("Закрыть", color = Acc2)
                }
            },
        )
    }
}
