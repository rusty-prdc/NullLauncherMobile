package org.nulllauncher.mobile.data.loaders

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.nulllauncher.mobile.core.InstallCancel
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.versions.Arguments
import org.nulllauncher.mobile.data.versions.VersionJson
import org.nulllauncher.mobile.data.versions.VersionManager
import java.io.File

@Serializable
data class LoaderVersion(
    val version: String,
    val stable: Boolean = false,
)

/**
 * Загрузчики модов:
 *  - Fabric (meta.fabricmc.net) и Quilt (meta.quiltmc.org) — официальные meta-сервисы,
 *    профиль ставится поверх vanilla с наследованием client.jar и ассетов
 *  - Forge/NeoForge — требуют официальный инсталлер, в UI отмечены как «в разработке»
 */
object LoaderService {

    private fun ua() = mapOf("User-Agent" to "nullLauncher-Mobile/0.1 (github.com/rusty-prdc/NullLauncherMobile)")

    private fun meta(kind: String): String = when (kind) {
        "fabric" -> "https://meta.fabricmc.net/v2"
        "quilt" -> "https://meta.quiltmc.org/v2"
        else -> throw LauncherException("Загрузчик не поддерживается", kind)
    }

    fun isSupported(kind: String): Boolean = kind == "fabric" || kind == "quilt"

    /** Все версии загрузчика (сверху стабильные) */
    suspend fun loaderVersions(kind: String): List<LoaderVersion> {
        val raw = Net.get("${meta(kind)}/versions/loader", ua())
        val all = Net.json.decodeFromString<List<LoaderVersion>>(raw)
        return all.sortedByDescending { it.stable }
    }

    /** Актуальная стабильная (или просто первая) версия загрузчика */
    suspend fun latestLoader(kind: String): LoaderVersion =
        loaderVersions(kind).firstOrNull()
            ?: throw LauncherException("Не удалось получить версии загрузчика", kind)

    /** Профиль загрузчика — json в формате Mojang с библиотеками и mainClass */
    suspend fun profile(kind: String, gameVersion: String, loaderVersion: String): VersionJson =
        Net.json.decodeFromString(Net.get("${meta(kind)}/versions/loader/$gameVersion/$loaderVersion/profile/json", ua()))

    /** id установленной сборки: "1.21.1-fabric-0.16.9" */
    fun combinedId(kind: String, gameVersion: String, loaderVersion: String): String =
        "$gameVersion-$kind-$loaderVersion"

    /**
     * Установка загрузчика: библиотеки профиля + объединённый json.
     * client.jar и ассеты переиспользуются от базовой версии (inheritsFrom).
     */
    suspend fun installLoader(
        ctx: Context,
        kind: String,
        gameVersion: String,
        loaderVersion: String,
        onProgress: (String, Long, Long) -> Unit,
    ) {
        val vm = VersionManager(ctx)
        val profile = profile(kind, gameVersion, loaderVersion)
        val combinedId = combinedId(kind, gameVersion, loaderVersion)
        val vdir = File(Storage.versions(ctx), combinedId).apply { mkdirs() }

        val base = vm.versionJson(gameVersion)
        val libs = vm.allLibraries(profile)
        var done = 0L
        onProgress("Библиотеки загрузчика", 0, libs.size.toLong())
        for (lib in libs) {
            if (InstallCancel.requested) throw LauncherException("Установка отменена", combinedId)
            val art = lib.downloads?.artifact ?: continue
            if (art.url.isBlank()) continue
            val parts = lib.name.split(":")
            if (parts.size < 3) continue
            val path = "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar"
            Net.downloadFile(art.url, File(Storage.libraries(ctx), path), art.sha1, art.size)
            done++
            onProgress("Библиотеки загрузчика ($done/${libs.size})", done, libs.size.toLong())
        }

        val argsJson = Net.json.encodeToString(profile.arguments ?: Arguments())
        val libsJson = Net.json.encodeToString(libs)
        val merged = """
            {
              "id": "$combinedId",
              "type": "release",
              "mainClass": "${profile.mainClass}",
              "assets": "${base.assets}",
              "assetIndex": ${base.assetIndex?.let { Net.json.encodeToString(it) } ?: "null"},
              "libraries": $libsJson,
              "arguments": $argsJson,
              "inheritsFrom": "$gameVersion"
            }
        """.trimIndent()
        File(vdir, "$combinedId.json").writeText(merged)
        onProgress("Готово", 1, 1)
    }
}
