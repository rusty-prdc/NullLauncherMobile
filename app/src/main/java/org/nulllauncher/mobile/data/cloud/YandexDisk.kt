package org.nulllauncher.mobile.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nulllauncher.mobile.core.LauncherException
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class DiskHref(val href: String = "")

@Serializable
data class DiskItem(
    val name: String = "",
    val type: String = "file",
    val size: Long = 0,
    val modified: String? = null,
)

@Serializable
data class DiskEmbedded(val items: List<DiskItem> = emptyList())

@Serializable
data class DiskListResponse(@SerialName("_embedded") val embedded: DiskEmbedded? = null)

/**
 * Клиент Яндекс Диска (cloud-api.yandex.net).
 * Авторизация: токен OAuth («Bearer»-схема OAuth <token>), который пользователь
 * вставляет сам — никаких секретов и client_id в приложении.
 */
class YandexDisk(private val token: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun enc(path: String) = URLEncoder.encode(path, "UTF-8")

    private fun apiRequest(url: String): Request =
        Request.Builder().url(url).header("Authorization", "OAuth $token").build()

    private fun httpError(code: Int): Nothing = throw LauncherException(
        "Яндекс Диск",
        when (code) {
            401 -> "Токен не принят — проверь токен в разделе «Облачные бэкапы»"
            403 -> "Нет прав на Диск — перевыпусти токен с правами Disk"
            429 -> "Слишком много запросов, подожди минуту"
            else -> "Ошибка Яндекс Диска: HTTP $code"
        },
    )

    /** Проверка токена: GET /v1/disk */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            http.newCall(apiRequest("$API/v1/disk")).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Создать папку (409 = уже существует, это норма) */
    suspend fun ensureFolder(path: String) = withContext(Dispatchers.IO) {
        val r = http.newCall(
            Request.Builder()
                .url("$API/v1/disk/resources?path=${enc(path)}")
                .header("Authorization", "OAuth $token")
                .put(ByteArray(0).toRequestBody())
                .build(),
        ).execute()
        r.use { if (!it.isSuccessful && it.code != 409) httpError(it.code) }
    }

    /** Загрузить файл на Диск (двухшагово: получить ссылку → PUT) */
    suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        val href = http.newCall(
            apiRequest("$API/v1/disk/resources/upload?path=${enc(remotePath)}&overwrite=true"),
        ).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) httpError(r.code)
            json.decodeFromString<DiskHref>(body).href
        }
        http.newCall(
            Request.Builder().url(href).put(local.asRequestBody("application/zip".toMediaType())).build(),
        ).execute().use { r -> if (!r.isSuccessful) httpError(r.code) }
    }

    /** Скачать файл с Диска (двухшагово: получить ссылка → GET) */
    suspend fun download(remotePath: String, dest: File) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val href = http.newCall(
            apiRequest("$API/v1/disk/resources/download?path=${enc(remotePath)}"),
        ).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) httpError(r.code)
            json.decodeFromString<DiskHref>(body).href
        }
        http.newCall(Request.Builder().url(href).build()).execute().use { r ->
            if (!r.isSuccessful) httpError(r.code)
            r.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /** Список файлов папки */
    suspend fun list(folder: String): List<DiskItem> = withContext(Dispatchers.IO) {
        http.newCall(
            apiRequest("$API/v1/disk/resources/list?path=${enc(folder)}&limit=200"),
        ).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) httpError(r.code)
            json.decodeFromString<DiskListResponse>(body).embedded?.items ?: emptyList()
        }
    }

    /** Удалить файл/папку */
    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        http.newCall(
            Request.Builder()
                .url("$API/v1/disk/resources?path=${enc(path)}")
                .header("Authorization", "OAuth $token")
                .delete()
                .build(),
        ).execute().use { r -> if (!r.isSuccessful && r.code != 404) httpError(r.code) }
    }

    companion object {
        private const val API = "https://cloud-api.yandex.net"
    }
}
