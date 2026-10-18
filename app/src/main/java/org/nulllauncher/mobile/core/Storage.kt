package org.nulllauncher.mobile.core

import android.content.Context
import java.io.File

/**
 * Корневые каталоги приложения:
 *   files/instances/<id>/   — отдельный .minecraft каждой сборки (моды, миры, ресурс-паки не смешиваются)
 *   files/versions/         — общие jar'ы версий и библиотек (дедупликация)
 *   files/assets/           — игровые ресурсы (общий пул Mojang)
 *   files/runtimes/         — Java Runtime
 *   files/logs/             — логи запусков
 */
object Storage {
    fun root(ctx: Context): File = ctx.filesDir
    fun instances(ctx: Context): File = File(ctx.filesDir, "instances").apply { mkdirs() }
    fun versions(ctx: Context): File = File(ctx.filesDir, "versions").apply { mkdirs() }
    fun libraries(ctx: Context): File = File(ctx.filesDir, "libraries").apply { mkdirs() }
    fun assets(ctx: Context): File = File(ctx.filesDir, "assets").apply { mkdirs() }
    fun runtimes(ctx: Context): File = File(ctx.filesDir, "runtimes").apply { mkdirs() }
    fun logs(ctx: Context): File = File(ctx.filesDir, "logs").apply { mkdirs() }
    fun natives(ctx: Context, versionId: String): File =
        File(ctx.filesDir, "natives/$versionId").apply { mkdirs() }

    fun instanceDir(ctx: Context, instanceId: String): File =
        File(instances(ctx), instanceId).apply { mkdirs() }
    fun instanceMods(ctx: Context, instanceId: String): File =
        File(instanceDir(ctx, instanceId), "mods").apply { mkdirs() }
    fun instanceSaves(ctx: Context, instanceId: String): File =
        File(instanceDir(ctx, instanceId), "saves").apply { mkdirs() }
    fun instanceResourcePacks(ctx: Context, instanceId: String): File =
        File(instanceDir(ctx, instanceId), "resourcepacks").apply { mkdirs() }
    fun instanceShaderPacks(ctx: Context, instanceId: String): File =
        File(instanceDir(ctx, instanceId), "shaderpacks").apply { mkdirs() }
    fun instanceDataPacks(ctx: Context, instanceId: String): File =
        File(instanceDir(ctx, instanceId), "datapacks").apply { mkdirs() }

    /** Свободное место в байтах на разделе files */
    fun freeBytes(ctx: Context): Long = root(ctx).usableSpace
    fun totalBytes(ctx: Context): Long = root(ctx).totalSpace

    /** Рекурсивный размер каталога в байтах */
    fun dirSize(f: File): Long {
        if (f.isFile) return f.length()
        var s = 0L
        f.listFiles()?.forEach { s += dirSize(it) }
        return s
    }

    /** Удаление с подтверждением — см. UI: всегда через диалог */
    fun deleteRecursively(f: File): Boolean = f.deleteRecursively()
}
