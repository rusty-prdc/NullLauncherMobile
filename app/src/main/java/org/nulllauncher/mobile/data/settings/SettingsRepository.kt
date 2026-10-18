package org.nulllauncher.mobile.data.settings

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore(name = "settings")

/**
 * Настройки приложения: DataStore (переживает перезапуск).
 * Ограничение памяти: не больше ~75% от физической RAM — защита от сбоя системы.
 */
class SettingsRepository(private val ctx: Context) {

    private object K {
        val MEMORY_MB = intPreferencesKey("memory_mb")
        val JAVA_PATH = stringPreferencesKey("java_path")
        val RENDER_SCALE = intPreferencesKey("render_scale")       // %
        val FULLSCREEN = booleanPreferencesKey("fullscreen")
        val TOUCH_CONTROLS = booleanPreferencesKey("touch_controls")
        val GUI_SCALE = intPreferencesKey("gui_scale")             // 0 = авто
        val LAST_INSTANCE = stringPreferencesKey("last_instance")
        val SNAPSHOTS = booleanPreferencesKey("snapshots")
        val YANDEX_TOKEN = stringPreferencesKey("yandex_token")
    }

    val memoryMb: Flow<Int> = ctx.ds.data.map { it[K.MEMORY_MB] ?: 2048 }
    val javaPath: Flow<String?> = ctx.ds.data.map { it[K.JAVA_PATH] }
    val renderScale: Flow<Int> = ctx.ds.data.map { it[K.RENDER_SCALE] ?: 100 }
    val fullscreen: Flow<Boolean> = ctx.ds.data.map { it[K.FULLSCREEN] ?: false }
    val touchControls: Flow<Boolean> = ctx.ds.data.map { it[K.TOUCH_CONTROLS] ?: true }
    val guiScale: Flow<Int> = ctx.ds.data.map { it[K.GUI_SCALE] ?: 0 }
    val lastInstanceId: Flow<String?> = ctx.ds.data.map { it[K.LAST_INSTANCE] }
    val showSnapshots: Flow<Boolean> = ctx.ds.data.map { it[K.SNAPSHOTS] ?: false }
    val yandexToken: Flow<String?> = ctx.ds.data.map { it[K.YANDEX_TOKEN] }

    suspend fun setMemoryMb(v: Int) = ctx.ds.edit { p ->
        p[K.MEMORY_MB] = v.coerceIn(minMemoryMb(), maxMemoryMb())
    }
    suspend fun setJavaPath(v: String?) = ctx.ds.edit { p ->
        if (v != null) p[K.JAVA_PATH] = v else p.remove(K.JAVA_PATH)
    }
    suspend fun setRenderScale(v: Int) = ctx.ds.edit { it[K.RENDER_SCALE] = v.coerceIn(50, 200) }
    suspend fun setFullscreen(v: Boolean) = ctx.ds.edit { it[K.FULLSCREEN] = v }
    suspend fun setTouchControls(v: Boolean) = ctx.ds.edit { it[K.TOUCH_CONTROLS] = v }
    suspend fun setGuiScale(v: Int) = ctx.ds.edit { it[K.GUI_SCALE] = v.coerceIn(0, 4) }
    suspend fun setLastInstance(v: String?) = ctx.ds.edit { p ->
        if (v != null) p[K.LAST_INSTANCE] = v else p.remove(K.LAST_INSTANCE)
    }
    suspend fun setShowSnapshots(v: Boolean) = ctx.ds.edit { it[K.SNAPSHOTS] = v }
    suspend fun setYandexToken(v: String?) = ctx.ds.edit { p ->
        if (v != null) p[K.YANDEX_TOKEN] = v else p.remove(K.YANDEX_TOKEN)
    }

    /** Токен Яндекс Диска для облачных бэкапов */
    suspend fun currentYandexToken(): String? = yandexToken.first()

    /** Минимум памяти для JVM в МБ */
    fun minMemoryMb(): Int = 512

    /**
     * Максимум памяти в МБ, который разрешено выделить игре.
     * ВАЖНО: на Android Runtime.maxMemory() — это лимит хипа процесса (~256 МБ),
     * а не физическая RAM. Используем ActivityManager, иначе вылетает Slider.
     */
    fun maxMemoryMb(): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val ramMb = if (am != null) {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            (info.totalMem / (1024 * 1024)).toInt()
        } else {
            4096
        }
        // 75% от RAM, но не больше 8 ГБ и не меньше минимума
        return (ramMb * 3 / 4).coerceIn(minMemoryMb(), 8192)
    }

    /** Безопасное текущее значение памяти (всегда в допустимом диапазоне) */
    suspend fun currentMemoryMb(): Int =
        memoryMb.first().coerceIn(minMemoryMb(), maxMemoryMb())

    /** Текущий путь к Java */
    suspend fun currentJavaPath(): String? = javaPath.first()

    /** Последний id выбранной сборки */
    suspend fun currentLastInstanceId(): String? = lastInstanceId.first()
}

