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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.ai.CrashAction
import org.nulllauncher.mobile.data.ai.CrashAnalyzer
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.ErrSoft
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Карточка диагноза вылета: локальный анализатор (бесплатно, офлайн)
 * + кнопка «Спросить ИИ» (Premium) — отправляет лог в чат с ИИ,
 * ИИ разбирает незнакомую ошибку и возвращает кнопки-действия.
 */
@Composable
fun CrashDiagnosisCard(
    app: NullApp,
    nav: NavController,
    log: String,
    instanceId: String?,
    ramMb: Int,
    modifier: Modifier = Modifier,
) {
    val diagnosis = remember(log) { CrashAnalyzer.analyze(log, instanceId, ramMb) }
    val premium = app.billing.isPremiumActive()

    if (diagnosis != null) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ErrSoft),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = Err, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(diagnosis.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Err)
                }
                Spacer(Modifier.height(6.dp))
                Text(diagnosis.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)

                if (diagnosis.actions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        diagnosis.actions.forEach { act ->
                            ActionButton(act, app, nav)
                        }
                    }
                }
            }
        }
    }

    // всегда доступна отправка лога ИИ (Premium) — разберёт и то, что не попало в правила
    if (log.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        if (premium) {
            OutlinedButton(
                onClick = {
                    app.aiContext.value = log.takeLast(20_000)
                    nav.navigate("ai_chat")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Acc2, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Спросить ИИ — разобрать лог", fontSize = 12.5.sp, color = Acc2, fontWeight = FontWeight.SemiBold)
            }
        } else {
            TextButton(onClick = { nav.navigate("ai") }) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Mute, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("ИИ-разбор лога — в Premium", fontSize = 12.sp, color = Mute)
            }
        }
    }
}

/** Кнопка-действие из диагноза (бесплатно) */
@Composable
private fun ActionButton(act: CrashAction, app: NullApp, nav: NavController) {
    val label = when (act) {
        is CrashAction.SetRam -> "Поднять до ${act.mb / 1024} ГБ"
        is CrashAction.InstallMod -> "Установить ${act.title}"
        CrashAction.InstallJava -> "Переустановить Java"
        CrashAction.Login -> "Перелогиниться"
        CrashAction.OpenCatalog -> "Каталог"
        CrashAction.ReinstallVersion -> "Переустановить версию"
    }
    TextButton(
        onClick = {
            when (act) {
                is CrashAction.SetRam -> {
                    app.instances.get(act.instanceId)?.let {
                        app.instances.update(it.copy(memoryMb = act.mb))
                    }
                    nav.navigate("instance/${act.instanceId}")
                }
                is CrashAction.InstallMod -> {
                    app.pendingCatalogQuery.value = act.slug
                    nav.navigate("catalog")
                }
                CrashAction.InstallJava, CrashAction.Login -> nav.navigate("profile")
                CrashAction.OpenCatalog -> nav.navigate("catalog")
                CrashAction.ReinstallVersion -> nav.navigate("versions")
            }
        },
    ) {
        Text(label, fontSize = 11.5.sp, color = Acc2, fontWeight = FontWeight.SemiBold)
    }
}