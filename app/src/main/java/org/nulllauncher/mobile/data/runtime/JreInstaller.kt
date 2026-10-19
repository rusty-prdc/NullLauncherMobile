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
import kotlinx.coroutines.launch
import java.io.File

/**
 * Авто-установка Java Runtime. Каждая мажорная версия живёт в своей папке
 * runtimes/jre<major>/, чтобы 8, 17, 21 и 25 не затирали друг друга.
 *
 * Источники: публичные сборки OpenJDK под Android (bionic), GPLv2+CE.
 */
object JreInstaller {

    private const val POJAV = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download"
    private const val OWN = "https://github.com/rusty-prdc/NullLauncherMobile/releases/download/runtimes"

    /** Поддерживаемые мажорные версии, от старой к новой */
    val SUPPORTED = listOf(8, 17, 21, 25)

    /** Ближайшая поддерживаемая версия, не ниже запрошенной */
    fun normalize(required: Int): Int = SUPPORTED.firstOrNull { it >= required } ?: SUPPORTED.last()

    /** Варианты источников; каждый вариант — набор архивов, которые распаковываются в одну папку */
    private fun sourcesFor(major: Int): List<List<String>> {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when (major) {
            8 -> listOf(listOf("$POJAV/jre8-40df388/jre8-arm64-20220811-release.tar.xz"))
            17 -> when (abi) {
                "x86_64" -> listOf(listOf("$POJAV/jre17-ec28559/jre17-x86_64-20210825-release.tar.xz"))
                "x86" -> listOf(listOf("$POJAV/jre17-ec28559/jre17-x86-20220225-release.tar.xz"))
                "armeabi-v7a" -> listOf(listOf("$POJAV/jre17-ec28559/jre17-arm-20210914-release.tar.xz"))
                else -> listOf(
                    listOf("$POJAV/jre17-ca01427/jre17-arm64-20220817-release.tar.xz"),
                    listOf("$POJAV/jre17-ec28559/jre17-arm64-20210825-release.tar.xz"),
                )
            }
            else -> listOf(listOf("$OWN/jre$major-bin-arm64.tar.xz", "$OWN/jre$major-universal.tar.xz"))
        }
    }

    fun dirFor(ctx: Context, major: Int): File = File(Storage.runtimes(ctx), "jre$major")

    /** bin/java нужной версии или null */
    fun installedJava(ctx: Context, major: Int): String? =
        TarXz.findJavaBinary(dirFor(ctx, major))?.absolutePath

    /** Любая установленная Java (для экрана профиля) */
    fun installedJava(ctx: Context): String? {
        for (m in SUPPORTED.reversed()) installedJava(ctx, m)?.let { return it }
        return null
    }

    fun installedMajors(ctx: Context): List<Int> = SUPPORTED.filter { installedJava(ctx, it) != null }

    fun isInstalled(ctx: Context): Boolean = installedJava(ctx) != null

    /** Состояние установки — общее для профиля, главной и диагностики вылетов */
    val busyMajor = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val busyStage = kotlinx.coroutines.flow.MutableStateFlow("")
    val busyProgress = kotlinx.coroutines.flow.MutableStateFlow(-1f)
    val lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Установка с общей трансляцией состояния. Не бросает исключение. */
    fun installShared(ctx: Context, major: Int): Boolean {
        if (busyMajor.value != null) return false
        kotlinx.coroutines.GlobalScope.launch {
            val m = normalize(major)
            busyMajor.value = m
            lastError.value = null
            busyProgress.value = -1f
            try {
                install(ctx, m) { stage, done, total ->
                    busyStage.value = stage
                    busyProgress.value = if (total > 0) done.toFloat() / total else -1f
                }
            } catch (e: Exception) {
                val le = e as? LauncherException
                lastError.value = if (le != null) "${le.title}: ${le.details}" else (e.message ?: "Не удалось установить Java")
            }
            busyMajor.value = null
        }
        return true
    }

    /** Скачивание → распаковка → chmod. total = -1 — размер неизвестен. */
    suspend fun install(
        ctx: Context,
        majorVersion: Int,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val major = normalize(majorVersion)
        val work = dirFor(ctx, major)
        val stage = "Скачивание Java $major"
        InstallCancel.begin()
        val archives = mutableListOf<File>()

        try {
            var lastError: Exception? = null
            var ok = false
            for (variant in sourcesFor(major)) {
                try {
                    archives.forEach { it.delete() }
                    archives.clear()
                    variant.forEachIndexed { i, url ->
                        val f = File(ctx.cacheDir, "jre$major-$i.tar.xz")
                        archives += f
                        val label = if (variant.size > 1) "$stage (${i + 1}/${variant.size})" else stage
                        onProgress(label, 0, -1)
                        Net.downloadFile(url, f, null, null) { done, total ->
                            if (InstallCancel.requested) throw LauncherException("Установка отменена", "java")
                            onProgress(label, done, total)
                        }
                    }
                    ok = true
                    break
                } catch (e: LauncherException) {
                    if (InstallCancel.requested) throw e
                    lastError = e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (!ok) {
                throw LauncherException(
                    "Не удалось скачать Java $major",
                    lastError?.message ?: "Проверь интернет и повтори",
                )
            }

            onProgress("Распаковка Java $major", 0, -1)
            work.deleteRecursively()
            work.mkdirs()
            archives.forEach { TarXz.extract(it, work) }

            val java = TarXz.findJavaBinary(work)
                ?: throw LauncherException("bin/java не найден в архиве", "Java $major")
            makeExecutable(work)

            onProgress("Java $major готова", 1, 1)
            java.absolutePath
        } catch (e: LauncherException) {
            work.deleteRecursively()
            throw e
        } catch (e: Exception) {
            work.deleteRecursively()
            throw LauncherException("Не удалось установить Java $major", e.message ?: e.javaClass.simpleName)
        } finally {
            archives.forEach { it.delete() }
        }
    }

    private fun makeExecutable(root: File) {
        root.walkTopDown().forEach { f ->
            if (f.isFile) {
                f.setExecutable(true, false)
                f.setReadable(true, false)
            }
        }
    }
}
