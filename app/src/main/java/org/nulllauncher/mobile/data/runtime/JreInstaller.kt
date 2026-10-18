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
 * OpenJDK 17, собранный под Android (bionic), а не десктопный Linux.
 * Архив .tar.xz распаковывается вручную (TarXz) в files/runtimes/jre17-<arch>/.
 */
object JreInstaller {

    private const val RELEASE_TAG = "jre17-ec28559"
    private const val BASE =
        "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/$RELEASE_TAG"

    private class AbiArchive(val url: String, val arch: String)

    /** Архив под ABI устройства (arm64 — основной сценарий) */
    private fun archiveForAbi(): AbiArchive {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when (abi) {
            "arm64-v8a" -> AbiArchive("$BASE/jre17-arm64-20210825-release.tar.xz", "arm64")
            "armeabi-v7a" -> AbiArchive("$BASE/jre17-arm-20210914-release.tar.xz", "arm")
            "x86_64" -> AbiArchive("$BASE/jre17-x86_64-20210825-release.tar.xz", "x86_64")
            "x86" -> AbiArchive("$BASE/jre17-x86-20220225-release.tar.xz", "x86")
            else -> AbiArchive("$BASE/jre17-arm64-20210825-release.tar.xz", "arm64")
        }
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
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val abi = archiveForAbi()
        val runtimes = Storage.runtimes(ctx)
        val work = File(runtimes, "jre17-${abi.arch}")
        val archive = File(ctx.cacheDir, "jre17-${abi.arch}.tar.xz")

        try {
            onProgress("Скачивание Java 17 (${abi.arch})", 0, -1)
            Net.downloadFile(abi.url, archive, null, null) { done, total ->
                onProgress("Скачивание Java 17 (${abi.arch})", done, total)
            }

            if (InstallCancel.requested) throw LauncherException("Установка отменена", "java")

            onProgress("Распаковка Java 17", 0, -1)
            work.deleteRecursively()
            work.mkdirs()
            TarXz.extract(archive, work)

            val java = TarXz.findJavaBinary(work)
                ?: throw LauncherException("bin/java не найден в архиве", abi.url)
            makeExecutable(work)

            onProgress("Java 17 готова", 1, 1)
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
