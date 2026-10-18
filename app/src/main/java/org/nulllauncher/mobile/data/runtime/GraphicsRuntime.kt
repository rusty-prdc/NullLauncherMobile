package org.nulllauncher.mobile.data.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nulllauncher.mobile.core.Storage
import java.io.File

/**
 * Графический стек Stage B (бесплатно для всех):
 * - assets/gl4es/libgl4es.so — трансляция OpenGL → GLES (GL4ES, лицензия zlib)
 * - .so из assets/lwjgl — LWJGL-нативники, собранные под Android arm64 (LGPL)
 * Кладутся в natives конкретной версии; GL4ES прелоадится JNI-мостом до JVM.
 */
object GraphicsRuntime {

    private const val TAG = "GraphicsRuntime"

    fun installed(ctx: Context, versionId: String): Boolean =
        File(Storage.natives(ctx, versionId), "libgl4es.so").exists() &&
            File(Storage.natives(ctx, versionId), "liblwjgl_opengl.so").exists()

    /** Установить графические .so в natives версии. Возвращает установленные файлы. */
    suspend fun install(ctx: Context, versionId: String): List<String> =
        withContext(Dispatchers.IO) {
            val dest = Storage.natives(ctx, versionId)
            dest.mkdirs()
            val done = mutableListOf<String>()

            listOf("gl4es" to listOf("libgl4es.so"), "lwjgl" to null).forEach { (dir, files) ->
                val names = files ?: (ctx.assets.list(dir) ?: emptyArray()).filter { it.endsWith(".so") }
                names.forEach { name ->
                    try {
                        val out = File(dest, name)
                        if (!out.exists() || out.length() == 0L) {
                            ctx.assets.open("$dir/$name").use { input ->
                                out.outputStream().use { output -> input.copyTo(output) }
                            }
                            out.setExecutable(true, false)
                            done += name
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "install $dir/$name failed", e)
                    }
                }
            }
            done
        }

    /**
     * Патченные классы org.lwjgl.glfw под Android (без X11 — через ANativeWindow).
     * Кладутся в начало classpath, чтобы переопределить стандартный lwjgl-glfw jar.
     */
    fun glfwPatchedJar(ctx: Context): File? {
        val out = File(ctx.filesDir, "lwjgl-patch/lwjgl-glfw-classes.jar")
        return try {
            if (!out.exists() || out.length() == 0L) {
                out.parentFile?.mkdirs()
                ctx.assets.open("lwjgl/lwjgl-glfw-classes.jar").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "glfw patch jar failed", e)
            null
        }
    }
}