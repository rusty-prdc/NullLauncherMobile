package org.nulllauncher.mobile.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.ai.AiChatAction
import org.nulllauncher.mobile.data.ai.AiChatMessage
import org.nulllauncher.mobile.data.ai.AiService
import org.nulllauncher.mobile.data.ai.CrashAction
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Чат с ИИ: разбор логов, вопросы по Minecraft, рекомендации с кнопками-действиями
 * («Установить мод», «Поднять память»). Premium + подключённый ИИ-сервер.
 */
@Composable
fun AiChatScreen(app: NullApp, nav: NavController) {
    val scope = rememberCoroutineScope()
    val messages by app.aiMessages.collectAsState()
    val premium = app.billing.isPremiumActive()
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // контекст (лог/ошибка) — подхватываем при входе и отправляем ИИ сразу
    LaunchedEffect(Unit) {
        val ctx = app.aiContext.value
        if (ctx != null) {
            app.aiMessages.value = app.aiMessages.value + AiChatMessage(
                role = "user",
                text = "Разбери мой вылет и подскажи, что делать.",
            )
            sendToAi(app, scope, ctx) { thinking = it }
            app.aiContext.value = null
        }
    }

    // автопрокрутка к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text("ИИ-помощник", style = MaterialTheme.typography.titleLarge)
                Text("Логи, вопросы по Minecraft, подбор модов", fontSize = 11.sp, color = Mute)
            }
        }

        if (!premium) {
            // ---------- без подписки: гейт ----------
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 320.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(AccSoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = Acc2, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text("Чат с ИИ — функция Premium", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ИИ разберёт лог вылета, найдёт причину и даст кнопки-действия, " +
                            "ответит на вопросы по Minecraft и подберёт моды.",
                        fontSize = 12.5.sp, color = Mute,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { nav.navigate("subscription") }) {
                        Text("Оформить Premium", color = Acc2, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else if (messages.isEmpty()) {
            // ---------- пустое состояние: подсказки ----------
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 340.dp)) {
                    Text("С чего начать", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Mute)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Разбери мой последний вылет",
                        "Какой мод добавить для автоматизации фермы?",
                        "Почему низкий FPS и что отключить?",
                    ).forEach { hint ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { input = hint }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        ) { Text(hint, fontSize = 12.5.sp, color = Acc2) }
                    }
                    if (!AiService.configured) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Сервер ИИ подключается — до этого чат отвечает заготовками.",
                            fontSize = 11.sp, color = Mute,
                        )
                    }
                }
            }
        } else {
            // ---------- лента сообщений ----------
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { messages.indexOf(it) * 1000L + it.text.hashCode() }) { msg ->
                    MessageBubble(msg, app = app, nav = nav)
                }
                if (thinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(modifier = Modifier.width(90.dp).clip(RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text("ИИ изучает…", fontSize = 11.sp, color = Mute)
                        }
                    }
                }
            }
        }

        // ---------- ввод ----------
        if (premium) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Спроси или вставь ошибку…", color = Mute) },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Acc,
                        unfocusedBorderColor = Line,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty() || thinking) return@IconButton
                        input = ""
                        app.aiMessages.value = app.aiMessages.value + AiChatMessage(role = "user", text = text)
                        sendToAi(app, scope, null) { thinking = it }
                    },
                    enabled = !thinking,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Отправить",
                        tint = if (thinking) Mute else Acc2,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/** Отправка в ИИ: добавляет «думает» → ждёт ответ → пушит ответ + действия */
private fun sendToAi(
    app: NullApp,
    scope: kotlinx.coroutines.CoroutineScope,
    crashContext: String?,
    onThinking: (Boolean) -> Unit,
) {
    onThinking(true)
    scope.launch {
        val history = app.aiMessages.value.filter { !it.pending }
        try {
            val (text, actions) = AiService.ask(
                history = history,
                crashContext = crashContext,
            )
            app.aiMessages.value = app.aiMessages.value + AiChatMessage(
                role = "assistant",
                text = text,
                actions = actions,
            )
        } catch (e: Exception) {
            app.aiMessages.value = app.aiMessages.value + AiChatMessage(
                role = "assistant",
                text = e.message ?: "Сервер ИИ недоступен",
                failed = true,
            )
        }
        onThinking(false)
    }
}

/** Пузырь сообщения + кнопки-действия из ответа ИИ */
@Composable
private fun MessageBubble(msg: AiChatMessage, app: NullApp, nav: NavController) {
    val mine = msg.role == "user"
    val bubbleColor = when {
        mine -> AccSoft
        msg.failed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(if (mine) 16.dp else 4.dp, if (mine) 4.dp else 16.dp, 16.dp, 16.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                msg.text,
                fontSize = 13.sp,
                color = if (msg.failed) Err else MaterialTheme.colorScheme.onBackground,
            )
        }

        // кнопки-действия: «Установить мод», «Поднять память»…
        if (msg.actions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            msg.actions.forEach { act ->
                val label = when (act.type) {
                    "install_mod" -> "Установить: ${act.title.ifBlank { act.slug }}"
                    "install_java" -> "Переустановить Java"
                    "open_instance" -> "Открыть настройки сборки"
                    "open_catalog" -> "Открыть каталог"
                    else -> act.title.ifBlank { act.type }
                }
                OutlinedButton(
                    onClick = { runAction(act, app, nav) },
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Text(label, fontSize = 12.sp, color = Acc2, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Выполнение действия-кнопки из ответа ИИ (навигация/запрос каталога) */
private fun runAction(act: AiChatAction, app: NullApp, nav: NavController) {
    when (act.type) {
        "install_mod" -> {
            // каталог откроется с этим запросом и сам выполнит поиск
            app.pendingCatalogQuery.value = act.slug
            nav.navigate("catalog")
        }
        "open_catalog" -> nav.navigate("catalog")
        "open_instance" -> nav.navigate("instance/${act.instanceId}")
        "install_java" -> nav.navigate("profile")
        else -> {}
    }
}
