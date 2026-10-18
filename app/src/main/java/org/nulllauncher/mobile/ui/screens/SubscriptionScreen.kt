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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.billing.BillingProducts
import org.nulllauncher.mobile.data.billing.SubscriptionManager
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Экран подписки (заготовка под монетизацию).
 * Премиум: безлимит сборок, модпаки, тач-редактор, облачные бэкапы, ИИ-помощник.
 */
@Composable
fun SubscriptionScreen(app: NullApp, nav: NavController, billing: SubscriptionManager) {
    val scope = rememberCoroutineScope()
    var purchasing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Text("Премиум подписка", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Acc.copy(alpha = .1f))
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Star, null, tint = Acc, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("nullLauncher Premium", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Безлимит сборок, ИИ-помощник и поддержка разработки", fontSize = 12.sp, color = Mute)
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Что даёт подписка", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                val features = listOf(
                    "Безлимитное количество сборок",
                    "Импорт модпаков (.mrpack / CurseForge)",
                    "Редактор сенсорного управления с профилями раскладок",
                    "Облачные бэкапы сборок и миров",
                    "ИИ-вопросы по Minecraft: ответы на вопросы об игре прямо в лаунчере",
                    "ИИ-анализатор ошибок и логов: объяснит вылет простыми словами",
                    "ИИ-анализатор нагрузки: узнаёт ОЗУ и процессор телефона, анализирует сборку и моды, делает отчёт",
                    "ИИ-подбор модов под твою сборку и версию, проверка совместимости",
                    "ИИ-оптимизация: настройки памяти и FPS под твоё устройство",
                    "Приоритетная техподдержка"
                )
                features.forEach { feature ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Acc2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(feature, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "ИИ-функции работают через ИИ-сервер проекта и подключаются постепенно — появляются в обновлениях. " +
                        "В бесплатной версии доступны до 3 сборок, ИИ-помощник входит только в Premium.",
                    fontSize = 11.sp, color = Mute,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Оформить подписку", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (purchasing) return@Button
                scope.launch {
                    purchasing = true
                    billing.purchasePremium { success ->
                        purchasing = false
                        if (success) nav.popBackStack()
                    }
                }
            },
            enabled = !purchasing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Acc),
        ) {
            if (purchasing) {
                Text("Оформление…", fontSize = 14.sp, color = Color.White)
            } else {
                Text("nullLauncher Premium — 149 ₽ / мес.", fontSize = 14.sp, color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { nav.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("Позже", fontSize = 14.sp, color = Mute)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Подписка оформляется через Google Play. ID продуктов: ${BillingProducts.PREMIUM_MONTHLY}, " +
                "${BillingProducts.PREMIUM_YEARLY} — зарегистрируйте в Play Console перед релизом.",
            fontSize = 10.5.sp, color = Mute,
        )
    }
}
