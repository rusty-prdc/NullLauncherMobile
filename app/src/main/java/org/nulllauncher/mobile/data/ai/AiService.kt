package org.nulllauncher.mobile.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.nulllauncher.mobile.core.LauncherException
import java.util.concurrent.TimeUnit

// ---------- модель чата ----------

/** Действие из ответа ИИ — кнопка под сообщением */
@Serializable
data class AiChatAction(
    val type: String = "",     // install_mod | install_java | open_instance | open_catalog
    val slug: String = "",
    val title: String = "",
    val instanceId: String = "",
    val mb: Int = 0,
)

@Serializable
data class AiChatMessage(
    val role: String = "assistant",   // user | assistant
    val text: String = "",
    val actions: List<AiChatAction> = emptyList(),
    val pending: Boolean = false,     // «думает…»
    val failed: Boolean = false,
)

@Serializable
private data class WireMessage(val role: String, val content: String)

@Serializable
private data class WireRequest(val messages: List<WireMessage>, @SerialName("web_search") val webSearch: Boolean = true)

@Serializable
private data class WireAction(
    val type: String = "",
    val slug: String = "",
    val title: String = "",
    @SerialName("instance_id") val instanceId: String = "",
    val mb: Int = 0,
)

@Serializable
private data class WireResponse(val text: String = "", val actions: List<WireAction> = emptyList())

/**
 * Клиент ИИ-чата собственного сервера.
 * BASE_URL пуст = сервер ещё не подключён — UI честно показывает это состояние.
 * Протокол: POST {BASE}/v1/chat  {messages, web_search} → {text, actions[]}
 * ИИ анализирует логи, при возможности ищет решение в интернете и возвращает
 * текст + кнопки действий (установить мод, поднять память и т.д.).
 */
object AiService {

    /** >>> АДРЕС ТВОЕГО ИИ-СЕРВЕРА: вставь сюда, напр. "https://ai.example.com" <<< */
    const val BASE_URL = ""

    val configured: Boolean get() = BASE_URL.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Отправить историю чата и получить ответ.
     * @param crashContext если не null — лог/ошибка, которую нужно разобрать ИИ
     */
    suspend fun ask(
        history: List<AiChatMessage>,
        crashContext: String? = null,
    ): Pair<String, List<AiChatAction>> {
        if (!configured) {
            throw LauncherException(
                "ИИ-сервер подключается",
                "Функция заработает в ближайшем обновлении — уже в разработке.",
            )
        }
        val wireMessages = buildList {
            if (crashContext != null) {
                add(
                    WireMessage(
                        role = "user",
                        content = "Разбери лиг лога игры на Android. Объясни причину вылета простыми " +
                            "словами и предложи конкретные действия (кнопками, если можно помочь " +
                            "установкой мода или настройкой). Если нашёл решение в интернете — используй его.\n\n" +
                            "ЛОГ:\n$crashContext",
                    ),
                )
            }
            history.forEach {
                if (it.text.isNotBlank()) add(WireMessage(it.role, it.text))
            }
        }
        val body = json.encodeToString(WireRequest(wireMessages)).toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$BASE_URL/v1/chat")
            .post(body)
            .header("User-Agent", "nullLauncher-Mobile/0.1")
            .build()
        val raw = http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw LauncherException("ИИ недоступен", "HTTP ${r.code}")
            t
        }
        val resp = json.decodeFromString<WireResponse>(raw)
        if (resp.text.isBlank()) throw LauncherException("ИИ вернул пустой ответ", "попробуй ещё раз")
        return resp.text to resp.actions.map {
            AiChatAction(it.type, it.slug, it.title, it.instanceId, it.mb)
        }
    }
}