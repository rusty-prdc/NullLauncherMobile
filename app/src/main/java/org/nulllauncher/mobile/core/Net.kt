package org.nulllauncher.mobile.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Net {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** GET с повторами; бросает LauncherException с заголовком */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            retry(3) {
                val req = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw LauncherException("Ошибка сети (HTTP ${resp.code})", url)
                    resp.body?.string() ?: throw LauncherException("Пустой ответ", url)
                }
            }
        }

    suspend fun getBytes(url: String, onProgress: ((Long) -> Unit)? = null): ByteArray =
        withContext(Dispatchers.IO) {
            retry(3) {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw LauncherException("Ошибка скачивания (HTTP ${resp.code})", url)
                    val body = resp.body ?: throw LauncherException("Пустой ответ", url)
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onProgress?.invoke(total)
                    }
                    out.toByteArray()
                }
            }
        }

    /** Скачивание файла в каталог с проверкой sha1 и прогрессом. Повторная попытка при ошибке. */
    suspend fun downloadFile(
        url: String,
        dest: File,
        sha1: String? = null,
        expectedSize: Long? = null,
        onProgress: ((done: Long, total: Long) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (dest.exists() && (sha1 == null || sha1(dest) == sha1)) return@withContext // уже есть — целостность ок
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        retry(3) {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw LauncherException("Ошибка скачивания (HTTP ${resp.code})", url)
                val body = resp.body ?: throw LauncherException("Пустой ответ", url)
                val total = body.contentLength().let { if (it > 0) it else (expectedSize ?: -1L) }
                java.io.FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(128 * 1024)
                    var done = 0L
                    while (true) {
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress?.invoke(done, total)
                    }
                }
            }
            if (sha1 != null && sha1(tmp) != sha1) {
                tmp.delete()
                throw LauncherException("Файл повреждён (sha1 не совпал)", url)
            }
        }
        if (!tmp.renameTo(dest)) {
            dest.delete()
            if (!tmp.renameTo(dest)) throw LauncherException("Не удалось сохранить файл", dest.absolutePath)
        }
    }

    fun sha1(file: File): String = file.inputStream().use { ins ->
        val md = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(128 * 1024)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun <T> retry(times: Int, block: suspend () -> T): T {
        var last: Exception? = null
        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: LauncherException) {
                last = e
                if (attempt == times - 1) throw e
            } catch (e: IOException) {
                last = e
                if (attempt == times - 1) throw LauncherException("Сеть недоступна", e.message ?: "", e)
            }
        }
        throw last ?: LauncherException("Неизвестная ошибка")
    }
}
