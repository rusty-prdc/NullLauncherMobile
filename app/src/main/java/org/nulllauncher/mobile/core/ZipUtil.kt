package org.nulllauncher.mobile.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** ZIP-архивы: упаковка папки сборки для бэкапа и обратная распаковка. */
object ZipUtil {

    /** Упаковать всю папку src в файл out (относительные пути, без сжатия каталогов). */
    fun zipDir(src: File, out: File) {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            src.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(src).path.replace('\\', '/')
                z.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Распаковать архив в dest с перезаписью (защита от zip-slip). */
    fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val destPath = dest.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var entry = z.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (out.canonicalPath.startsWith(destPath)) {
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { z.copyTo(it) }
                    }
                }
                z.closeEntry()
                entry = z.nextEntry
            }
        }
    }
}
