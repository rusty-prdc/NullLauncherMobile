package org.nulllauncher.mobile.data.versions

import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Менеджер версий: официальный манифест Mojang (piston-meta),
 * скачивание client.jar, библиотек, ассетов с проверкой sha1.
 */
class VersionManager(private val ctx: Context) {

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val ASSET_URL = "https://resources.download.minecraft.net"

        /** id установленной сборки Fabric: "1.21.1-fabric-0.16.9" */
        fun fabricId(gameVersion: String, loaderVersion: String) = "$gameVersion-fabric-$loaderVersion"
    }

    private var cachedManifest: VersionManifest? = null

    suspend fun manifest(force: Boolean = false): VersionManifest {
        cachedManifest?.let { if (!force) return it }
        val m = Net.json.decodeFromString<VersionManifest>(Net.get(MANIFEST_URL))
        cachedManifest = m
        return m
    }

    suspend fun versionJson(id: String, force: Boolean = false): VersionJson {
        val file = File(Storage.versions(ctx), "$id/$id.json")
        if (file.exists() && !force) {
            return Net.json.decodeFromString(file.readText())
        }
        val mv = manifest().versions.firstOrNull { it.id == id }
            ?: throw org.nulllauncher.mobile.core.LauncherException("Версия не найдена", id)
        val json = Net.get(mv.url)
        file.parentFile?.mkdirs()
        file.writeText(json)
        return Net.json.decodeFromString(json)
    }

    /** Установлена ли версия. Для сборок с inheritsFrom (Fabric) client.jar переиспользуется базовый. */
    fun isInstalled(id: String): Boolean {
        val dir = File(Storage.versions(ctx), id)
        val json = File(dir, "$id.json")
        if (!json.exists()) return false
        val jar = File(dir, "$id.jar")
        if (jar.exists() && jar.length() > 0) return true
        // Fabric/Quilt: проверяем, что базовая vanilla-версия установлена
        val inherits = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(json.readText())?.groupValues?.get(1)
        return inherits != null && File(Storage.versions(ctx), "$inherits/$inherits.jar").let { it.exists() && it.length() > 0 }
    }

    /** Реальный id установленной сборки загрузчика для версии игры (иначе сама версия) */
    fun installedVersionId(gameVersion: String, loader: String): String? {
        if (loader == "vanilla") return gameVersion.takeIf { isInstalled(it) }
        val prefix = "$gameVersion-$loader-"
        return Storage.versions(ctx).listFiles()
            ?.map { it.name }
            ?.filter { it.startsWith(prefix) }
            ?.sortedDescending()
            ?.firstOrNull { isInstalled(it) }
    }

    /** Установка версии: client.jar + библиотеки + ассеты. Прогресс: стадия, сделано, всего. */
    suspend fun install(
        id: String,
        onProgress: (stage: String, done: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val vj = versionJson(id, force = true)
        val vdir = File(Storage.versions(ctx), id).apply { mkdirs() }

        // 1. client.jar
        val client = vj.downloads?.client
            ?: throw org.nulllauncher.mobile.core.LauncherException("Версия без client.jar", id)
        onProgress("Клиент игры", 0, client.size)
        Net.downloadFile(client.url, File(vdir, "$id.jar"), client.sha1, client.size) { d, t ->
            onProgress("Клиент игры", d, if (t > 0) t else client.size)
        }

        // 2. Библиотеки (правила ОС: на Android считаем linux — чистые Java-библиотеки качаем всегда)
        val libs = allLibraries(vj)
        var done = 0L
        onProgress("Библиотеки", 0, libs.size.toLong())
        for (lib in libs) {
            val art = lib.downloads?.artifact ?: continue
            if (art.url.isBlank()) continue
            // путь из coordinates: group/artifact/version/artifact-version.jar
            val parts = lib.name.split(":")
            if (parts.size < 3) continue
            val path = "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar"
            val dest = File(Storage.libraries(ctx), path)
            Net.downloadFile(art.url, dest, art.sha1, art.size)
            done++
            onProgress("Библиотеки ($done/${libs.size})", done, libs.size.toLong())
        }

        // 3. Ассеты
        vj.assetIndex?.let { ai ->
            val indexFile = File(Storage.assets(ctx), "indexes/${ai.id}.json")
            Net.downloadFile(ai.url, indexFile, ai.sha1.ifBlank { null })
            val index = Net.json.decodeFromString<AssetIndex>(indexFile.readText())
            val objs = index.objects
            var ad = 0L
            val totalBytes = objs.values.sumOf { it.size }
            onProgress("Ресурсы игры", 0, totalBytes)
            for ((_, obj) in objs) {
                val sub = obj.hash.substring(0, 2)
                val dest = File(Storage.assets(ctx), "objects/$sub/${obj.hash}")
                Net.downloadFile("$ASSET_URL/$sub/${obj.hash}", dest, obj.hash, obj.size) { d, t ->
                    onProgress("Ресурсы игры ($ad/${objs.size})", ad + d, totalBytes)
                }
                ad++
            }
        }

        // Проверка места перед стартом — уже сделана по факту, но зафиксируем размер
        onProgress("Готово", 1, 1)
    }

    /** Все библиотеки с учётом правил ОС (оставляем linux + без правил) */
    fun allLibraries(vj: VersionJson): List<Library> =
        vj.libraries.filter { lib ->
            val rules = lib.rules ?: return@filter true
            // без os-правил — разрешено; с правилами: разрешено, если os.name не задан (общее) или linux
            val osRules = rules.filter { it.os?.name != null }
            if (osRules.isEmpty()) return@filter true
            osRules.any { it.action == "allow" && (it.os?.name == "linux" || it.os?.name == null) }
        }

    fun freeSpaceText(): String {
        val free = Storage.freeBytes(ctx)
        val total = Storage.totalBytes(ctx)
        return "занято ${fmt(total - free)} · свободно ${fmt(free)}"
    }

    /** Количество установленных каталогов версий (с json+jar) */
    fun versionsDirCount(): Int =
        Storage.versions(ctx).listFiles()?.count { dir ->
            dir.isDirectory && File(dir, "${dir.name}.json").exists() &&
                File(dir, "${dir.name}.jar").exists()
        } ?: 0

    fun fmt(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> "%.0f МБ".format(bytes / (1024.0 * 1024))
        else -> "$bytes Б"
    }
}
