package org.nulllauncher.mobile

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nulllauncher.mobile.data.accounts.Account
import org.nulllauncher.mobile.data.accounts.AccountService
import org.nulllauncher.mobile.data.billing.SubscriptionManager
import org.nulllauncher.mobile.data.instances.InstanceRepository
import org.nulllauncher.mobile.data.launch.LaunchService
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.data.settings.SettingsRepository
import org.nulllauncher.mobile.data.versions.VersionManager
import java.io.File

data class InstallProgress(val stage: String, val done: Long, val total: Long)

/** Запись журнала загрузок */
data class DownloadRecord(
    val name: String,
    val detail: String,
    val size: Long,
    val at: Long,
    val ok: Boolean,
) {
    fun toJson(): String = "$at|$ok|$size|$name|$detail"

    companion object {
        fun fromJson(line: String): DownloadRecord? {
            val p = line.split("|")
            if (p.size < 5) return null
            return DownloadRecord(
                name = p[3],
                detail = p[4],
                size = p[2].toLongOrNull() ?: 0,
                at = p[0].toLongOrNull() ?: 0,
                ok = p[1] == "true",
            )
        }
    }
}

/** Точка сборки всех сервисов (простая без-ди-инфраструктура) */
class NullApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val instances by lazy { InstanceRepository(this) }
    val versions by lazy { VersionManager(this) }
    val accounts by lazy { AccountService(this) }
    val launcher by lazy { LaunchService(this) }

    /** Биллинг и подписка (заготовка под Google Play) */
    val billing by lazy { SubscriptionManager(this) }

    /** Прогресс установки компонентов для главного экрана */
    val installProgress = MutableStateFlow<InstallProgress?>(null)

    /** Последняя ошибка для показа на главном экране */
    val lastError = MutableStateFlow<String?>(null)

    /** Контекст для ИИ-чата (лой/ошибка) — подхватывается при открытии чата */
    val aiContext = MutableStateFlow<String?>(null)

    /** Сообщения ИИ-чата (живут между экранами) */
    val aiMessages = MutableStateFlow<List<org.nulllauncher.mobile.data.ai.AiChatMessage>>(emptyList())

    /** Запрос каталога из кнопки-действия ИИ/анализатора */
    val pendingCatalogQuery = MutableStateFlow<String?>(null)

    /** Журнал загрузок (вкладка «Загрузки») */
    val downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())

    fun logDownload(name: String, detail: String, size: Long, ok: Boolean) {
        val rec = DownloadRecord(name, detail, size, System.currentTimeMillis(), ok)
        downloads.value = listOf(rec) + downloads.value
        try {
            val f = java.io.File(filesDir, "downloads.json")
            val lines = f.takeIf { it.exists() }?.readLines().orEmpty()
            f.writeText((listOf(rec.toJson()) + lines).take(200).joinToString("\n"))
        } catch (_: Throwable) {
        }
    }

    fun loadDownloads() {
        try {
            val f = java.io.File(filesDir, "downloads.json")
            if (!f.exists()) return
            downloads.value = f.readLines().mapNotNull { DownloadRecord.fromJson(it) }
        } catch (_: Throwable) {
        }
    }

    /** Журнал последнего запуска */
    val launchLog = MutableStateFlow("")
    fun appendLog(line: String) {
        launchLog.value = (launchLog.value + "\n" + line).takeLast(8000)
    }

    /** Текущий аккаунт (первый) */
    fun currentAccount(): Account? = accounts.accounts().firstOrNull()

    fun modCount(instanceId: String): Int =
        ModrinthService.installedMods(File(filesDir, "instances/$instanceId/mods")).size

    /** Сколько версий Minecraft установлено на диске */
    fun versionsCount(): Int =
        versions.versionsDirCount()

    override fun onCreate() {
        super.onCreate()
        instance = this
        billing.connect()

        // Глобальный ловитель падений: пишем стек в файл, чтобы разобраться с вылетами
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(filesDir, "logs").apply { mkdirs() }
                File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(
                    "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "sdk=${android.os.Build.VERSION.SDK_INT}\n" +
                        throwable.stackTraceToString(),
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: NullApp
            private set
    }
}
