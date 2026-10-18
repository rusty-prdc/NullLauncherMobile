package org.nulllauncher.mobile.data.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nulllauncher.mobile.core.InstallCancel
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.core.TarXz
import java.io.File

/**
 * Авто-установка Java Runtime.
 *
 * Источник: публичные релизы OpenJDK, собранные под Android (bionic),
 * из репозитория сборок PojavLauncherTeam (совместимые с лицензией GPLv2+CE).
 * Архив .tar.xz распаковывается вручную (TarXz) в files/runtimes/jre<ver>-<arch>/.
 *
 * Поддерживаемые версии: 8, 17. Версия 21 отдаётся как 17 (сборки 21 для Android
 * в公開 релизах пока отсутствуют).
 */
object JreInstaller {

    private const val BASE = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download"

    private class AbiArchive(val url: String, val arch: String)

    /** Архив под версию Java и ABI устройства (arm64 — основной сценарий) */
    private fun archiveFor(majorVersion: Int): AbiArchive {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val tag = when (majorVersion) {
            8 -> "jre8-40df388"
            else -> "jre17-ec28559" // 16, 17, 21 (21 пока Falls back к 17)
        }
        val (fileName, arch) = when (abi) {
            "arm64-v8a" -> if (majorVersion == 8) "jre8-arm64-20210914-release.tar.xz" to "arm64" else "jre17-arm64-20210825-release.tar.xz" to "arm64"
            "armeabi-v7a" -> "jre8-arm-20210914-release.tar.xz" to "arm"
            "x86_64" -> "jre17-x86_64-20210825-release.tar.xz" to "x86_64"
            "x86" -> "jre17-x86-20220225-release.tar.xz" to "x86"
            else -> "jre17-arm64-20210825-release.tar.xz" to "arm64"
        }
        return AbiArchive("$BASE/$tag/$fileName", arch)
    }

    /** Уже установлен? Возвращает путь к bin/java или null */
    fun installedJava(ctx: Context): String? {
        Storage.runtimes(ctx).listFiles()?.forEach { dir ->
            TarXz.findJavaBinary(dir)?.let { return it.absolutePath }
        }
        return null
    }

    fun isInstalled(ctx: Context): Boolean = installedJava(ctx) != null

    /**
     * Полная установка: скачивание → распаковка → chmod.
     * onProgress(stage, done, total), total = -1 — без известного размера.
     */
    suspend fun install(
        ctx: Context,
        majorVersion: Int,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val abi = archiveFor(majorVersion)
        val runtimes = Storage.runtimes(ctx)
        val work = File(runtimes, "jre${abi.arch}")
        val archive = File(ctx.cacheDir, "jre${majorVersion}-${abi.arch}.tar.xz")

        try {
            onProgress("Скачивание Java $majorVersion (${abi.arch})", 0, -1)
            Net.downloadFile(abi.url, archive, null, null) { done, total ->
                onProgress("Скачивание Java $majorVersion (${abi.arch})", done, total)
            }

            if (InstallCancel.requested) throw LauncherException("Установка отменена", "java")

            onProgress("Распаковка Java $majorVersion", 0, -1)
            work.deleteRecursively()
            work.mkdirs()
            TarXz.extract(archive, work)

            val java = TarXz.findJavaBinary(work)
                ?: throw LauncherException("bin/java не найден в архиве", abi.url)
            makeExecutable(work)

            onProgress("Java $majorVersion готова", 1, 1)
            java.absolutePath
        } finally {
            archive.delete()
        }
    }

    /** chmod +x для файлов рантайма (иначе JVM не запустится) */
    private fun makeExecutable(root: File) {
        root.walkTopDown().forEach { f ->
            if (f.isFile) {
                f.setExecutable(true, false)
                f.setReadable(true, false)
            }
        }
    }
}
