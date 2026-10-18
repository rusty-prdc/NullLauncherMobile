package org.nulllauncher.mobile.data.instances

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import java.io.File

/**
 * Сборка (профиль): независимый .minecraft — моды, миры, ресурс-паки и настройки
 * хранятся отдельно для каждой сборки и не смешиваются.
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val versionId: String,           // версия Minecraft, напр. "1.21.1"
    val loader: String = "vanilla",  // vanilla | fabric | forge | neoforge | quilt
    val loaderVersion: String? = null,
    val memoryMb: Int = 2048,        // память этой сборки
    val javaPath: String? = null,    // отдельный Java Runtime (null = глобальный)
    val coverEmoji: String = "🟩",   // обложка (пока эмодзи-иконка)
    val description: String = "",    // описание сборки (из мастера)
    val colorHex: String = "#3ecf8e",// цвет акцента сборки (из мастера)
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
)

class InstanceRepository(private val ctx: Context) {

    private val file get() = File(ctx.filesDir, "instances.json")

    fun list(): List<Instance> {
        if (!file.exists()) return emptyList()
        return try {
            Net.json.decodeFromString<List<Instance>>(file.readText())
                .sortedByDescending { it.lastPlayedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Instance? = list().firstOrNull { it.id == id }

    @Synchronized
    fun save(all: List<Instance>) {
        file.writeText(Net.json.encodeToString(all))
    }

    fun create(name: String, versionId: String, loader: String = "vanilla", loaderVersion: String? = null): Instance {
        val id = "inst-" + System.currentTimeMillis().toString(36)
        val inst = Instance(id = id, name = name, versionId = versionId, loader = loader, loaderVersion = loaderVersion)
        val all = list().toMutableList()
        all.add(inst)
        save(all)
        Storage.instanceDir(ctx, id) // создаём каталог
        return inst
    }

    fun update(inst: Instance) {
        val all = list().map { if (it.id == inst.id) inst else it }
        save(all)
    }

    /** Копия сборки: конфиг + mods + resourcepacks; миры копируются отдельно по желанию */
    fun copy(id: String, newName: String, copyWorlds: Boolean = false): Instance? {
        val src = get(id) ?: return null
        val inst = src.copy(
            id = "inst-" + System.currentTimeMillis().toString(36),
            name = newName,
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = 0,
        )
        val all = list().toMutableList()
        all.add(inst)
        save(all)

        val srcDir = Storage.instanceDir(ctx, src.id)
        val dstDir = Storage.instanceDir(ctx, inst.id)
        listOf("mods", "resourcepacks", "config", "options.txt").forEach { rel ->
            val s = File(srcDir, rel)
            if (s.exists()) s.copyRecursively(File(dstDir, rel), overwrite = true)
        }
        if (copyWorlds) {
            val saves = File(srcDir, "saves")
            if (saves.exists()) saves.copyRecursively(File(dstDir, "saves"), overwrite = true)
        }
        return inst
    }

    /** Удаление с подтверждением (UI всегда показывает диалог перед вызовом) */
    fun delete(id: String) {
        val all = list().filterNot { it.id == id }
        save(all)
        Storage.deleteRecursively(Storage.instanceDir(ctx, id))
    }

    fun markPlayed(id: String) {
        get(id)?.let { update(it.copy(lastPlayedAt = System.currentTimeMillis())) }
    }
}
