package org.nulllauncher.mobile

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nulllauncher.mobile.data.accounts.Account
import org.nulllauncher.mobile.data.accounts.AccountService
import org.nulllauncher.mobile.data.instances.InstanceRepository
import org.nulllauncher.mobile.data.launch.LaunchService
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.data.settings.SettingsRepository
import org.nulllauncher.mobile.data.versions.VersionManager
import java.io.File

data class InstallProgress(val stage: String, val done: Long, val total: Long)

/** Точка сборки всех сервисов (простая без-ди-инфраструктура) */
class NullApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val instances by lazy { InstanceRepository(this) }
    val versions by lazy { VersionManager(this) }
    val accounts by lazy { AccountService(this) }
    val launcher by lazy { LaunchService(this) }

    /** Прогресс установки компонентов для главного экрана */
    val installProgress = MutableStateFlow<InstallProgress?>(null)

    /** Последняя ошибка для показа на главном экране */
    val lastError = MutableStateFlow<String?>(null)

    /** Журнал последнего запуска */
    val launchLog = MutableStateFlow("")
    fun appendLog(line: String) {
        launchLog.value = (launchLog.value + "\n" + line).takeLast(8000)
    }

    /** Текущий аккаунт (первый) */
    fun currentAccount(): Account? = accounts.accounts().firstOrNull()

    fun modCount(instanceId: String): Int =
        ModrinthService.installedMods(File(filesDir, "instances/$instanceId/mods")).size

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NullApp
            private set
    }
}
