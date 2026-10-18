package org.nulllauncher.mobile.data.launch

import android.util.Log

/**
 * Stage B — JNI-мост (libnative-bridge.so): запуск JVM внутри процесса приложения.
 * Dlopen'ит libjvm.so из Android-сборки OpenJDK, создаёт JVM, зовёт main() игры.
 * Работа сопровождается логом в файл (stderr перенаправляется туда же).
 */
object NativeBridge {

    private const val TAG = "NativeBridge"

    /** Коды возврата — синхронны с native_bridge.c */
    const val OK = 0
    const val DLOPEN_FAIL = -1
    const val NO_SYMBOL = -2
    const val VM_FAIL = -3
    const val NO_MAIN = -4
    const val MAIN_THREW = -5
    const val BAD_ARGS = -6

    private var loaded = false
    private var loadFailed = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadFailed) return false
        return try {
            System.loadLibrary("native-bridge")
            loaded = true
            Log.i(TAG, "native-bridge loaded")
            true
        } catch (t: Throwable) {
            loadFailed = true
            Log.e(TAG, "loadLibrary failed", t)
            false
        }
    }

    /**
     * Запуск main-класса игры в собственной JVM (блокирующий — звать из IO-потока).
     * @return NB_OK (0) либо код ошибки NB_*
     */
    external fun launch(
        libjvm: String,
        classpath: String,
        mainClass: String,
        args: Array<String>,
        gameDir: String,
        nativesDir: String,
        logFile: String,
        memoryMb: Int,
    ): Int

    /** Человекочитаемая причина по коду */
    fun explain(code: Int): String = when (code) {
        DLOPEN_FAIL -> "Не удалось загрузить JVM (libjvm.so повреждён или несовместим) — переустанови Java"
        NO_SYMBOL -> "Символ JNI_CreateJavaVM не найден — эта Java не подходит, переустанови Java"
        VM_FAIL -> "JVM не запустилась — попробуй уменьшить память до 2 ГБ"
        NO_MAIN -> "Главный класс игры не найден — переустанови версию"
        MAIN_THREW -> "Игра завершилаcя с исключением — смотри лог"
        BAD_ARGS -> "Внутренняя ошибка запуска"
        else -> "Игра завершилась (код $code)"
    }
}