package org.nulllauncher.mobile.data.versions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** piston-meta.mojang.com — корневой манифест всех версий */
@Serializable
data class VersionManifest(
    val latest: Latest,
    val versions: List<ManifestVersion>,
)

@Serializable
data class Latest(val release: String, val snapshot: String)

@Serializable
data class ManifestVersion(
    val id: String,
    val type: String, // release | old_alpha | old_beta | snapshot
    val url: String,
    val time: String = "",
    @SerialName("releaseTime") val releaseTime: String = "",
    val sha1: String = "",
)

/** Полный JSON конкретной версии (client.json) */
@Serializable
data class VersionJson(
    val id: String,
    val type: String = "release",
    val mainClass: String = "",
    val assets: String = "",
    @SerialName("assetIndex") val assetIndex: AssetIndexRef? = null,
    val downloads: Downloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    val javaVersion: JavaVersion? = null,
    val complianceLevel: Int = 0,
    val inheritsFrom: String? = null, // у Fabric/Quilt
)

@Serializable
data class AssetIndexRef(val id: String, val url: String, val sha1: String = "", val totalSize: Long = 0)

@Serializable
data class Downloads(val client: DownloadInfo? = null, val server: DownloadInfo? = null)

@Serializable
data class DownloadInfo(val sha1: String = "", val size: Long = 0, val url: String = "")

@Serializable
data class Library(
    val name: String = "",
    val downloads: LibraryDownloads? = null,
    val natives: Map<String, String>? = null,
    val rules: List<Rule>? = null,
    val url: String = "", // зеркало maven
)

@Serializable
data class LibraryDownloads(
    val artifact: DownloadInfo? = null,
    val classifiers: Map<String, DownloadInfo>? = null,
)

@Serializable
data class Rule(val action: String, val os: OsRule? = null)

@Serializable
data class OsRule(val name: String? = null)

@Serializable
data class Arguments(
    @SerialName("game") val minecraft: List<ArgumentEl> = emptyList(),
    val jvm: List<ArgumentEl> = emptyList(),
)

/**
 * Аргумент в json Mojang — либо просто строка ("${author_name}"),
 * либо объект {"rules": [...], "value": "..." | [...]}.
 */
@Serializable(with = ArgumentElSerializer::class)
data class ArgumentEl(
    val value: JsonElement? = null,
    val rules: List<Rule>? = null,
)

object ArgumentElSerializer : KSerializer<ArgumentEl> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: kotlinx.serialization.Decoder): ArgumentEl {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Ожидался JsonDecoder")
        return when (val el = input.decodeJsonElement()) {
            is JsonPrimitive -> ArgumentEl(value = el)
            is JsonObject -> ArgumentEl(
                value = el["value"],
                rules = el["rules"]?.let { arr ->
                    Json.decodeFromJsonElement(JsonArray.serializer(), arr).map {
                        Json.decodeFromJsonElement(Rule.serializer(), it)
                    }
                },
            )
            else -> ArgumentEl()
        }
    }

    override fun serialize(encoder: kotlinx.serialization.Encoder, value: ArgumentEl) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Ожидался JsonEncoder")
        if (value.rules.isNullOrEmpty()) {
            output.encodeJsonElement(value.value ?: JsonNull)
        } else {
            output.encodeJsonElement(buildJsonObject {
                put("rules", Json.encodeToJsonElement(ListSerializer(Rule.serializer()), value.rules))
                value.value?.let { put("value", it) }
            })
        }
    }
}

@Serializable
data class JavaVersion(val component: String = "", val majorVersion: Int = 17)

/** Индекс ассетов (objects/...) */
@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
)

@Serializable
data class AssetObject(val hash: String, val size: Long = 0)
