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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.accounts.Account
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok

/**
 * Профиль: аккаунты Microsoft (device-code вход) + настройки производительности.
 */
@Composable
fun ProfileScreen(app: NullApp, nav: NavController2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf(app.currentAccount()) }
    var deviceCode by remember { mutableStateOf<org.nulllauncher.mobile.data.accounts.AccountService.DeviceCode?>(null) }
    var waiting by remember { mutableStateOf(false) }

    var memoryMb by remember { mutableFloatStateOf(2048f) }
    var maxMemory by remember { mutableStateOf(4096) }
    var fullscreen by remember { mutableStateOf(false) }
    var touchControls by remember { mutableStateOf(true) }
    var renderScale by remember { mutableFloatStateOf(100f) }

    LaunchedEffect(Unit) {
        maxMemory = app.settings.maxMemoryMb()
        memoryMb = app.settings.memoryMb.first().toFloat()
        fullscreen = app.settings.fullscreen.first()
        touchControls = app.settings.touchControls.first()
        renderScale = app.settings.renderScale.first().toFloat()
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
        Card(shape = RoundedCornerShape(18.dp)) {
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
                                try {
                                    val dc = app.accounts.requestDeviceCode()
                                    deviceCode = dc
                                    waiting = true
                                    // открываем страницу подтверждения
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))
                                    context.startActivity(intent)
                                    // опрашиваем токен
                                    val ms = app.accounts.pollToken(dc.deviceCode)
                                    val acc = app.accounts.loginWithMicrosoft(ms)
                                    app.accounts.add(acc)
                                    account = acc
                                } catch (e: Exception) {
                                    app.lastError.value = e.message ?: "Ошибка входа"
                                }
                                waiting = false
                                deviceCode = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D46C8)),
                    ) { Text("Войти через Microsoft", fontWeight = FontWeight.SemiBold) }

                    if (waiting && deviceCode != null) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text("Код: ${deviceCode!!.userCode}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Acc)
                        Text("Введи его на ${deviceCode!!.verificationUri}", fontSize = 12.sp, color = Mute)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Card2),
                            contentAlignment = Alignment.Center,
                        ) { Text("🧑‍🌾", fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Microsoft · ${account!!.uuid.take(8)}…", fontSize = 11.sp, color = Mute)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try { account = app.accounts.refresh(account!!) }
                                catch (e: Exception) { app.lastError.value = "Токен не обновился: ${e.message}" }
                            }
                        }) { Text("Обновить", fontSize = 12.sp, color = Ok) }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        account?.let { app.accounts.remove(it.uuid) }
                        account = null
                    }) { Text("Выйти из аккаунта", fontSize = 12.sp, color = Err) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- память ----------
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Оперативная память", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("${"%.1f".format(memoryMb / 1024f)} ГБ", color = Color(0xFFC4B0FF), fontSize = 13.5.sp)
                }
                Slider(
                    value = memoryMb,
                    onValueChange = { memoryMb = it },
                    onValueChangeFinished = { scope.launch { app.settings.setMemoryMb(memoryMb.toInt()) } },
                    valueRange = 512f..maxMemory.toFloat(),
                )
                Text(
                    "макс. безопасный: ${"%.1f".format(maxMemory / 1024f)} ГБ — больше нельзя без риска сбоя",
                    fontSize = 11.sp, color = Mute,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- графика ----------
        Card(shape = RoundedCornerShape(18.dp)) {
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
                    Text("${renderScale.toInt()}%", color = Color(0xFFC4B0FF), fontSize = 13.5.sp)
                }
                Slider(
                    value = renderScale,
                    onValueChange = { renderScale = it },
                    onValueChangeFinished = { scope.launch { app.settings.setRenderScale(renderScale.toInt()) } },
                    valueRange = 50f..150f,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- место ----------
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Хранилище", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(app.versions.freeSpaceText(), fontSize = 12.sp, color = Mute)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

typealias NavController2 = androidx.navigation.NavController
