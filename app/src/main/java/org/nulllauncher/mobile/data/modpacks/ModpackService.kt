package org.nulllauncher.mobile.data.modpacks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.ZipUtil
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class MrpackFile(
    val path: String = "",
    val downloads: List<String> = emptyList(),
    val env: MrpackEnv? = null,
)

@Serializable
data class MrpackEnv(val client: String = "required")

@Serializable
data class MrpackIndex(
    val formatVersion: Int = 0,
    val game: String = "",
    val versionId: String = "",
    val name: String = "",
    val files: List<MrpackFile> = emptyList(),
    val dependencies: Map<String, String> = emptyMap(),
)

data class ModpackProgress(val done: Int, val total: Int, val current: String)

/**
 * Импорт модпаков .mrpack (формат Modrinth):
 * ZIP → modrinth.index.json → файлы по путям (mods/, config/…) в папку сборки.
 * Загрузчик определяется из dependencies (fabric-loader / quilt-loader / forge / neoforge).
 */
object ModpackService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val UA = "nullLauncher-Mobile/0.1 (github.com/rusty-prdc/NullLauncherMobile)"

    /** Скачать .mrpack по ссылке во временный файл */
    private fun downloadPack(url: String, dest: File) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw LauncherException("Не удалось скачать модпак", "HTTP ${r.code}")
            r.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /** Определить загрузчик и версию игры из зависимостей пака */
    fun loaderOf(index: MrpackIndex): String = when {
        index.dependencies.containsKey("fabric-loader") -> "fabric"
        index.dependencies.containsKey("quilt-loader") -> "quilt"
        index.dependencies.containsKey("neoforge") -> "neoforge"
        index.dependencies.containsKey("forge") -> "forge"
        else -> "vanilla"
    }

    fun gameVersionOf(index: MrpackIndex): String = index.dependencies["minecraft"] ?: ""

    /**
     * Шаг 1: скачать .mrpack и распаковать во временную папку, вернуть индекс.
     * (Имя класса — dirs папки + индекс; освобождать через release().)
     */
    data class FetchedPack(val tempDir: File, val index: MrpackIndex) {
        fun release() {
            tempDir.deleteRecursively()
        }
    }

    suspend fun fetch(url: String, onStage: (String) -> Unit): FetchedPack =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tmpPack = File.createTempFile("pack", ".mrpack")
            val tmpDir = File.createTempFile("packx", "").apply { delete(); mkdirs() }
            try {
                onStage("Скачивание модпака…")
                downloadPack(url, tmpPack)
                onStage("Чтение содержимого…")
                ZipUtil.unzip(tmpPack, tmpDir)
            } catch (e: Exception) {
                tmpDir.deleteRecursively()
                throw e
            } finally {
                tmpPack.delete()
            }
            val indexFile = File(tmpDir, "modrinth.index.json")
            if (!indexFile.exists()) {
                tmpDir.deleteRecursively()
                throw LauncherException(
                    "Это не модпак Modrinth",
                    "В архиве нет modrinth.index.json — поддерживается формат .mrpack",
                )
            }
            val index = json.decodeFromString<MrpackIndex>(indexFile.readText())
            if (index.game != "minecraft") {
                tmpDir.deleteRecursively()
                throw LauncherException("Не Minecraft-пак", index.game)
            }
            FetchedPack(tmpDir, index)
        }

    /** Шаг 2: разложить файлы пака по папкам созданной сборки */
    suspend fun installFiles(
        pack: FetchedPack,
        instanceDir: File,
        onProgress: (ModpackProgress) -> Unit,
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val files = pack.index.files.filter { f -> f.env?.client != "disabled" && (f.downloads.isNotEmpty() || File(pack.tempDir, f.path).exists()) }
        var done = 0
        try {
            for (f in files) {
                val target = File(instanceDir, f.path)
                target.parentFile?.mkdirs()
                val src = File(pack.tempDir, f.path)
                if (src.exists()) {
                    src.copyTo(target, overwrite = true)
                } else {
                    val url0 = f.downloads.firstOrNull()
                        ?: throw LauncherException("Нет источника", f.path)
                    val req = Request.Builder().url(url0).header("User-Agent", UA).build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw LauncherException("Ошибка загрузки", "${f.path} (HTTP ${r.code})")
                        r.body?.byteStream()?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                done++
                onProgress(ModpackProgress(done, files.size, f.path.substringAfterLast('/')))
            }
        } finally {
            pack.release()
        }
    }

    /** Импорт из локального .mrpack/.zip (выбранного файлом) */
    fun importLocalMrpack(packFile: File, instanceDir: File): MrpackIndex {
        val tmpDir = File.createTempFile("packx", "").apply { delete(); mkdirs() }
        try {
            ZipUtil.unzip(packFile, tmpDir)
            val indexFile = File(tmpDir, "modrinth.index.json")
            if (!indexFile.exists()) throw LauncherException("Это не модпак Modrinth", "нет modrinth.index.json")
            val index = json.decodeFromString<MrpackIndex>(indexFile.readText())
            index.files.filter { it.env?.client != "disabled" }.forEach { f ->
                val src = File(tmpDir, f.path)
                if (src.exists()) {
                    val target = File(instanceDir, f.path)
                    target.parentFile?.mkdirs()
                    src.copyTo(target, overwrite = true)
                }
            }
            return index
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
