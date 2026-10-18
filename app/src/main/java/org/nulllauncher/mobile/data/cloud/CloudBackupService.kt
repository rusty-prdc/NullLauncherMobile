package org.nulllauncher.mobile.data.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.core.ZipUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudBackupInfo(
    val remotePath: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
)

/**
 * Облачные бэкапы сборок (Premium): ZIP архив папки сборки → Яндекс Диск.
 * Формат имени: <instId>_<yyyyMMdd-HHmmss>.zip в папке nullLauncher/backups.
 */
object CloudBackupService {

    const val REMOTE_FOLDER = "/nullLauncher/backups"
    private const val NAME_TIME = "yyyyMMdd-HHmmss"

    /** Упаковать папку сборки во временный ZIP */
    suspend fun zipInstance(ctx: Context, instanceId: String): File = withContext(Dispatchers.IO) {
        val src = Storage.instanceDir(ctx, instanceId)
        if (!src.exists()) throw LauncherException("Сборка не найдена", instanceId)
        val out = File(ctx.cacheDir, "backup-$instanceId-${System.currentTimeMillis()}.zip")
        ZipUtil.zipDir(src, out)
        out
    }

    /** Создать бэкап и загрузить на Диск */
    suspend fun create(ctx: Context, instanceId: String, disk: YandexDisk): CloudBackupInfo =
        withContext(Dispatchers.IO) {
            val zip = try {
                zipInstance(ctx, instanceId)
            } catch (e: Exception) {
                throw e
            }
            try {
                if (zip.length() == 0L) throw LauncherException("Пустая сборка", "нечего сохранять")
                disk.ensureFolder(REMOTE_FOLDER)
                val stamp = SimpleDateFormat(NAME_TIME, Locale.US).format(Date())
                val remote = "$REMOTE_FOLDER/${instanceId}_$stamp.zip"
                disk.upload(zip, remote)
                CloudBackupInfo(remote, "${instanceId}_$stamp.zip", zip.length(), System.currentTimeMillis())
            } finally {
                zip.delete()
            }
        }

    /** Список бэкапов (новые сверху) */
    suspend fun list(disk: YandexDisk, instanceId: String? = null): List<CloudBackupInfo> =
        withContext(Dispatchers.IO) {
            disk.list(REMOTE_FOLDER)
                .filter { it.type == "file" && it.name.endsWith(".zip") }
                .filter { instanceId == null || it.name.startsWith("${instanceId}_") }
                .map {
                    CloudBackupInfo(
                        remotePath = "$REMOTE_FOLDER/${it.name}",
                        name = it.name,
                        size = it.size,
                        modifiedAt = parseTime(it.name, it.modified),
                    )
                }
                .sortedByDescending { it.modifiedAt }
        }

    /** Скачать бэкап и распаковать поверх папки сборки */
    suspend fun restore(ctx: Context, backup: CloudBackupInfo, disk: YandexDisk) =
        withContext(Dispatchers.IO) {
            val instanceId = backup.name.substringBefore("_")
            if (Storage.instanceDir(ctx, instanceId).let { !it.exists() }) {
                throw LauncherException(
                    "Сборка не найдена",
                    "Сначала создай сборку $instanceId — потом восстанавливай бэкап",
                )
            }
            val tmp = File(ctx.cacheDir, "restore-${backup.name}")
            try {
                disk.download(backup.remotePath, tmp)
                ZipUtil.unzip(tmp, Storage.instanceDir(ctx, instanceId))
            } finally {
                tmp.delete()
            }
        }

    suspend fun delete(disk: YandexDisk, backup: CloudBackupInfo) = disk.delete(backup.remotePath)

    private fun parseTime(fileName: String, modified: String?): Long {
        // имя: <instId>_yyyyMMdd-HHmmss.zip
        val stamp = fileName.substringBeforeLast(".zip").substringAfterLast("_")
        return try {
            SimpleDateFormat(NAME_TIME, Locale.US).parse(stamp)?.time ?: 0
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(modified ?: "")?.time ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }
}
