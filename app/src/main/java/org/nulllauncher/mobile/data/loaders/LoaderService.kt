package org.nulllauncher.mobile.data.loaders

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.versions.VersionJson
import org.nulllauncher.mobile.data.versions.VersionManager
import android.content.Context
import java.io.File

@Serializable
data class FabricLoaderVersion(val version: String, val stable: Boolean = false)

/**
 * Загрузчики модов:
 *  - Fabric/Quilt — официальные meta-сервисы, лёгкая интеграция
 *  - Forge/NeoForge — Maven-координаты, ставятся как набор библиотек поверх vanilla
 */
object LoaderService {

    // ---------- Fabric ----------

    suspend fun fabricLoaders(gameVersion: String): List<FabricLoaderVersion> =
        Net.json.decodeFromString(
            Net.get(
                "https://meta.fabricmc.net/v2/versions/loader",
                mapOf("User-Agent" to "nullLauncher-Mobile/0.1")
            )
        )

    /** Профиль Fabric для версии игры: JSON с библиотеками и mainClass */
    suspend fun fabricProfile(gameVersion: String, loaderVersion: String): VersionJson {
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
        val json = Net.get(url, mapOf("User-Agent" to "nullLauncher-Mobile/0.1"))
        return Net.json.decodeFromString(json)
    }

    /** Установка Fabric: скачивает библиотеки загрузчика в libraries и пишет объединённый json версии */
    suspend fun installFabric(
        ctx: Context,
        gameVersion: String,
        loaderVersion: String,
        onProgress: (String, Long, Long) -> Unit,
    ) {
        val vm = VersionManager(ctx)
        val profile = fabricProfile(gameVersion, loaderVersion)
        val combinedId = "$gameVersion-fabric-$loaderVersion"
        val vdir = File(Storage.versions(ctx), combinedId).apply { mkdirs() }

        val base = vm.versionJson(gameVersion) // vanilla json обязателен
        // библиотеки профиля Fabric
        val libs = vm.allLibraries(profile)
        var done = 0L
        onProgress("Библиотеки Fabric", 0, libs.size.toLong())
        for (lib in libs) {
            val art = lib.downloads?.artifact ?: continue
            if (art.url.isBlank()) continue
            val parts = lib.name.split(":")
            if (parts.size >= 3) {
                val path = "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar"
                Net.downloadFile(art.url, File(Storage.libraries(ctx), path), art.sha1, art.size)
            }
            done++
            onProgress("Библиотеки Fabric ($done/${libs.size})", done, libs.size.toLong())
        }

        // объединённый json: vanilla + параметры Fabric
        val merged = """{
            "id": "$combinedId",
            "type": "release",
            "mainClass": "${profile.mainClass}",
            "assets": "${base.assets}",
            "assetIndex": ${base.assetIndex?.let { Net.json.encodeToString(it) } ?: "null"},
            "downloads": ${base.downloads?.let { """{"client":{"sha1":"","size":0,"url":""}}""" } ?: "null"},
            "libraries": ${Net.json.encodeToString(libs)},
            "arguments": ${Net.json.encodeToString(profile.arguments ?: org.nulllauncher.mobile.data.versions.Arguments())},
            "inheritsFrom": "$gameVersion",
            "javaVersion": ${base.javaVersion?.let { """{"component":"${it.component}","majorVersion":${it.majorVersion}}""" } ?: "null"}
        }"""
        File(vdir, "$combinedId.json").writeText(merged)
        // client.jar переиспользуем vanilla (inheritsFrom)
        onProgress("Готово", 1, 1)
    }

    fun fabricInstalledId(gameVersion: String, loaderVersion: String): String =
        "$gameVersion-fabric-$loaderVersion"

    // ---------- Forge / NeoForge (базовая поддержка) ----------

    /** Мостовые версии Forge: полный список через maven-metadata */
    suspend fun forgeVersions(gameVersion: String): List<String> {
        val xml = Net.get(
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
            mapOf("User-Agent" to "nullLauncher-Mobile/0.1")
        )
        val versions = Regex("<version>([^<]+)</version>").findAll(xml)
            .map { it.groupValues[1] }
            .filter { it.startsWith("$gameVersion-") }
            .map { it.removePrefix("$gameVersion-") }
            .toList()
        return versions.take(30)
    }
}
