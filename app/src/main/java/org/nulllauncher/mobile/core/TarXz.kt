package org.nulllauncher.mobile.core

import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Распаковка .tar.xz без внешних утилит (на Android нет tar/unc).
 * Поддерживает обычные файлы, каталоги, симлинки и GNU long names.
 */
object TarXz {

    fun extract(archive: File, destDir: File) {
        destDir.mkdirs()
        XZInputStream(archive.inputStream().buffered()).use { input ->
            val header = ByteArray(512)
            var longName: String? = null

            while (true) {
                if (!readFully(input, header)) break
                if (header.all { it.toInt() == 0 }) break // конец архива

                val nameField = cstr(header, 0, 100)
                val size = octal(header, 124, 12)
                val type = header[156].toInt().toChar()

                when (type) {
                    'L' -> { // GNU long name: имя лежит в теле записи
                        longName = readDataString(input, size)
                        continue
                    }
                    'K' -> { // GNU long link name — пропускаем вместе с телом
                        skipData(input, size)
                        continue
                    }
                }

                val name = longName ?: run {
                    val prefix = cstr(header, 345, 155)
                    if (prefix.isNotEmpty()) "$prefix/$nameField" else nameField
                }
                longName = null
                if (name.isBlank()) {
                    skipData(input, size)
                    continue
                }

                val out = File(destDir, name)
                // защита от path traversal
                val canonicalOut = out.canonicalPath
                val canonicalRoot = destDir.canonicalPath
                if (canonicalOut != canonicalRoot && !canonicalOut.startsWith(canonicalRoot + File.separator)) {
                    skipData(input, size)
                    continue
                }

                when (type) {
                    '5', 'D' -> out.mkdirs()
                    '2' -> { // симлинк
                        val target = readDataString(input, size)
                        out.parentFile?.mkdirs()
                        try {
                            out.delete()
                            java.nio.file.Files.createSymbolicLink(out.toPath(), File(target).toPath())
                        } catch (_: Exception) {
                            // симлинк не создался — для jre некритично
                        }
                    }
                    '0', ' ', '7' -> { // обычный файл
                        out.parentFile?.mkdirs()
                        out.outputStream().use { os -> copyExactly(input, os, size) }
                    }
                    else -> skipData(input, size)
                }
                val pad = (512 - (size % 512)) % 512 // выравнивание до 512
                skipData(input, pad)
            }
        }
    }

    /** Найти bin/java внутри распакованного дерева (архив может содержать верхнюю папку) */
    fun findJavaBinary(root: File): File? {
        val direct = File(root, "bin/java")
        if (direct.exists()) return direct
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val nested = File(dir, "bin/java")
                if (nested.exists()) return nested
            }
        }
        return null
    }

    // ---------- helpers ----------

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var end = off
        val max = off + len
        while (end < max && b[end].toInt() != 0) end++
        return String(b, off, end - off, Charsets.US_ASCII).trim()
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        val s = cstr(b, off, len).trim()
        if (s.isEmpty()) return 0L
        return s.toLongOrNull(8) ?: 0L
    }

    private fun readDataString(input: InputStream, size: Long): String {
        val data = ByteArray(size.coerceAtMost(4096).toInt())
        readFully(input, data)
        if (size > data.size) skipData(input, size - data.size)
        return String(data, Charsets.UTF_8).trimEnd(' ').trim()
    }

    private fun skipData(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun copyExactly(input: InputStream, out: java.io.OutputStream, size: Long) {
        val buf = ByteArray(128 * 1024)
        var remaining = size
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) throw IOException("Архив оборван: не хватило $remaining байт")
            out.write(buf, 0, n)
            remaining -= n
        }
    }
}
