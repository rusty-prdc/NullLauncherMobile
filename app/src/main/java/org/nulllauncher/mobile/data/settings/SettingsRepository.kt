package org.nulllauncher.mobile.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.ds by preferencesDataStore(name = "settings")

/**
 * Настройки приложения: DataStore (переживает перезапуск).
 * Ограничение памяти: не больше ~75% от физической RAM — защита от сбоя системы.
 */
class SettingsRepository(private val ctx: Context) {

    companion object {
        /** Максимально безопасная память для JVM в байтах */
        fun safeMaxMemoryBytes(): Long {
            val rt = Runtime.getRuntime()
            val total = rt.totalMemory() + rt.maxMemory() // приближение к физической
            return (total * 0.75).toLong()
        }
    }

    private object K {
        val MEMORY_MB = intPreferencesKey("memory_mb")
        val JAVA_PATH = stringPreferencesKey("java_path")
        val RENDER_SCALE = intPreferencesKey("render_scale")       // %
        val FULLSCREEN = booleanPreferencesKey("fullscreen")
        val TOUCHControls = booleanPreferencesKey("touch_controls")
        val GUI_SCALE = intPreferencesKey("gui_scale")             // 0 = авто
        val LAST_INSTANCE = stringPreferencesKey("last_instance")
        val SNAPSHOT_FILTER = booleanPreferencesKey("snapshots")
    }

    val memoryMb: Flow<Int> = ctx.ds.data.map { it[K.MEMORY_MB] ?: 2048 }
    val javaPath: Flow<String?> = ctx.ds.data.map { it[K.JAVA_PATH] }
    val renderScale: Flow<Int> = ctx.ds.data.map { it[K.RENDER_SCALE] ?: 100 }
    val fullscreen: Flow<Boolean> = ctx.ds.data.map { it[K.FULLSCREEN] ?: false }
    val touchControls: Flow<Boolean> = ctx.ds.data.map { it[K.TOUCHControls] ?: true }
    val guiScale: Flow<Int> = ctx.ds.data.map { it[K.GUI_SCALE] ?: 0 }
    val lastInstanceId: Flow<String?> = ctx.ds.data.map { it[K.LAST_INSTANCE] }
    val showSnapshots: Flow<Boolean> = ctx.ds.data.map { it[K.SNAPSHOT_FILTER] ?: false }

    suspend fun setMemoryMb(v: Int) = ctx.ds.edit { it[K.MEMORY_MB] = v.coerceIn(512, maxMemoryMb()) }
    suspend fun setJavaPath(v: String?) = ctx.ds.edit { p ->
        if (v != null) p[K.JAVA_PATH] = v else p.remove(K.JAVA_PATH)
    }
    suspend fun setRenderScale(v: Int) = ctx.ds.edit { it[K.RENDER_SCALE] = v.coerceIn(50, 200) }
    suspend fun setFullscreen(v: Boolean) = ctx.ds.edit { it[K.FULLSCREEN] = v }
    suspend fun setTouchControls(v: Boolean) = ctx.ds.edit { it[K.TOUCHControls] = v }
    suspend fun setGuiScale(v: Int) = ctx.ds.edit { it[K.GUI_SCALE] = v.coerceIn(0, 4) }
    suspend fun setLastInstance(v: String?) = ctx.ds.edit { p ->
        if (v != null) p[K.LAST_INSTANCE] = v else p.remove(K.LAST_INSTANCE)
    }
    suspend fun setShowSnapshots(v: Boolean) = ctx.ds.edit { it[K.SNAPSHOT_FILTER] = v }

    /** Максимум в МБ, который разрешено выделить (безопасный предел) */
    fun maxMemoryMb(): Int = (safeMaxMemoryBytes() / (1024 * 1024)).toInt().coerceAtMost(8192)

    suspend fun currentMemoryMb(): Int = memoryMb.first()

    /** Синхронное чтение пути Java (для запуска вне Flow) */
    fun javaPathValue(): String? = runBlocking { javaPath.first() }
}
