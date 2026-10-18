package org.nulllauncher.mobile.data.modrinth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import java.io.File

@Serializable
data class SearchResponse(
    val hits: List<ModHit> = emptyList(),
    val totalHits: Int = 0,
)

@Serializable
data class ModHit(
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val downloads: Long = 0,
    @SerialName("project_id") val projectId: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val projectType: String = "mod",
)

@Serializable
data class ProjectDetail(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    val author: String = "",
    val downloads: Long = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
)

@Serializable
data class ProjectVersion(
    val id: String = "",
    val name: String = "",
    val versionNumber: String = "",
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val files: List<VersionFile> = emptyList(),
    val datePublished: String = "",
)

@Serializable
data class VersionFile(
    val url: String = "",
    val filename: String = "",
    val primary: Boolean = false,
    val size: Long = 0,
    @SerialName("hashes") val hashes: Hashes? = null,
)

@Serializable
data class Hashes(val sha1: String? = null, val sha512: String? = null)

/**
 * Каталог Modrinth (api.modrinth.com/v2).
 * Поиск, фильтры по версии MC и загрузчику, установка модов и модпаков.
 */
object ModrinthService {
    const val API = "https://api.modrinth.com/v2"

    /** User-Agent обязателен по правилам Modrinth */
    private fun ua() = mapOf("User-Agent" to "nullLauncher-Mobile/0.1 (github.com/rusty-prdc/NullLauncherMobile)")

    suspend fun search(
        query: String,
        gameVersion: String? = null,
        loader: String? = null,
        projectType: String = "mod",
        limit: Int = 20,
    ): SearchResponse {
        val facets = buildList {
            add("[\"project_type:$projectType\"]")
            gameVersion?.let { add("[\"versions:$it\"]") }
            loader?.let { add("[\"categories:$it\"]") }
        }.joinToString(",", "[", "]")
        val url = "$API/search?query=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&facets=$facets&limit=$limit&index=relevance"
        return Net.json.decodeFromString(Net.get(url, ua()))
    }

    suspend fun project(idOrSlug: String): ProjectDetail =
        Net.json.decodeFromString(Net.get("$API/project/$idOrSlug", ua()))

    suspend fun versions(idOrSlug: String): List<ProjectVersion> =
        Net.json.decodeFromString(Net.get("$API/project/$idOrSlug/version", ua()))

    /** Лучшая версия мода под сборку: версия MC + загрузчик, иначе последняя */
    suspend fun bestVersion(idOrSlug: String, gameVersion: String, loader: String): ProjectVersion? {
        val all = versions(idOrSlug)
        return all.firstOrNull { gameVersion in it.gameVersions && loader in it.loaders }
            ?: all.firstOrNull { gameVersion in it.gameVersions }
            ?: all.firstOrNull()
    }

    /** Установка файла версии в mods сборки (с проверкой sha1) */
    suspend fun installFile(
        file: VersionFile,
        destDir: File,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): File {
        destDir.mkdirs()
        val dest = File(destDir, file.filename)
        Net.downloadFile(file.url, dest, file.hashes?.sha1, file.size, onProgress)
        return dest
    }

    /** Установлен ли мод в сборку — по имени файла */
    fun isInstalled(modsDir: File, filename: String): Boolean =
        File(modsDir, filename).exists() || File(modsDir, "$filename.disabled").exists()

    /** Включение/выключение мода (.jar ↔ .jar.disabled) */
    fun toggle(modsDir: File, filename: String, enabled: Boolean) {
        val f = File(modsDir, filename)
        val disabled = File(modsDir, "$filename.disabled")
        if (enabled && disabled.exists()) disabled.renameTo(f)
        if (!enabled && f.exists()) f.renameTo(disabled)
    }

    fun installedMods(modsDir: File): List<File> =
        modsDir.listFiles()?.filter { it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun deleteMod(modsDir: File, file: File) {
        file.delete()
    }

    /** Размер установленных модов сборки */
    fun modsSize(modsDir: File): Long = Storage.dirSize(modsDir)
}
