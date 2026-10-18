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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Заготовка AI-экрана: функции гейтятся Premium-подпиской.
 * Сам ИИ-сервер подключается позже — сейчас UI и статусы.
 */
@Composable
fun AIScreen(app: NullApp, nav: NavController) {
    val premium = app.billing.isPremiumActive()
    var requestedFeature by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text("ИИ-помощник", style = MaterialTheme.typography.titleLarge)
                Text("Подбор модов, диагностика вылетов, оптимизация", fontSize = 11.sp, color = Mute)
            }
            if (premium) {
                Box(
                    Modifier.padding(end = 12.dp).clip(RoundedCornerShape(999.dp)).background(AccSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Premium", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Acc2)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!premium) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(AccSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Lock, null, tint = Acc2, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("ИИ доступен по Premium", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "ИИ-помощник анализирует логи, подбирает моды под твою сборку и подсказывает оптимизацию FPS. Функция входит в подписку Premium.",
                                fontSize = 12.sp, color = Mute,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { nav.navigate("subscription") },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Acc),
                            ) {
                                Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Оформить Premium", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("Что умеет", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }

                val features = listOf(
                    "Анализ логов" to "Объясню простыми словами, почему игра вылетела или не запускается.",
                    "Подбор модов" to "Подберу моды под твою сборку и версию, проверю совместимость.",
                    "Оптимизация FPS" to "Подскажу, что отключить и как настроить для плавной игры.",
                )
                items(features.size) { i ->
                    val (title, desc) = features[i]
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(desc, fontSize = 12.sp, color = Mute)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { requestedFeature = title },
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("Запросить", fontSize = 12.sp, color = Acc2)
                                }
                                Spacer(Modifier.width(10.dp))
                                if (requestedFeature == title) {
                                    Text(
                                        "ИИ-сервер подключается — заработает в ближайшем обновлении.",
                                        fontSize = 10.5.sp, color = Acc2, modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Card2)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Статус подключения", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Mute)
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = Line)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Собственный ИИ-сервер пока не подключён — экран работает, запросы обрабатываются после подключения API.",
                                fontSize = 11.sp, color = Mute,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
