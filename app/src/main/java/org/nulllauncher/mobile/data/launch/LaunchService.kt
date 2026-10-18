package org.nulllauncher.mobile.data.launch

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.core.TarXz
import org.nulllauncher.mobile.data.accounts.Account
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.runtime.JreInstaller
import org.nulllauncher.mobile.data.versions.VersionJson
import org.nulllauncher.mobile.data.versions.VersionManager
import java.io.File
import java.util.concurrent.TimeUnit

sealed class LaunchState {
    data object Idle : LaunchState()
    data class Preparing(val stage: String) : LaunchState()
    data class Running(val pid: Int) : LaunchState()
    data class Failed(val title: String, val details: String) : LaunchState()
    data object Stopped : LaunchState()
}

/**
 * Запуск Minecraft Java: сборка полной команды (JVM-аргументы, classpath, natives,
 * аргументы игры из json версии) и исполнение через Java Runtime.
 *
 * На Android запуск JVM-игры требует совместимого Java-окружения с графическим
 * мостом (OpenGL-трансляция). Путь к рантайму задаётся в настройках
 * (SettingsRepository.javaPath). Если рантайма нет — понятная ошибка, без фейков.
 */
class LaunchService(private val ctx: Context) {

    private val _state = MutableStateFlow<LaunchState>(LaunchState.Idle)
    val state: StateFlow<LaunchState> = _state

    private var process: Process? = null

    companion object {
        private const val TAG = "NullLauncher"
        const val OFFLINE_NAME = "Player"
    }

    /** Проверка: можно ли запускать сборку (файлы версии на месте) */
    fun checkInstance(inst: Instance): String? {
        val vm = VersionManager(ctx)
        val versionId = resolveVersionId(inst)
        return if (vm.isInstalled(versionId)) null
        else if (inst.loader != "vanilla")
            "Загрузчик ${inst.loader} для ${inst.versionId} не установлен — установи его в настройках сборки"
        else "Minecraft ${inst.versionId} не установлен — установи в менеджере версий"
    }

    fun resolveVersionId(inst: Instance): String {
        val vm = VersionManager(ctx)
        return vm.installedVersionId(inst.versionId, inst.loader)
            ?: VersionManager.fabricId(inst.versionId, inst.loaderVersion ?: "latest")
    }

    suspend fun start(
        inst: Instance,
        account: Account?,
        javaPath: String?,
        onLog: ((String) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            _state.value = LaunchState.Preparing("Проверка файлов")
            val vm = VersionManager(ctx)
            val versionId = resolveVersionId(inst)
            val vdir = File(Storage.versions(ctx), versionId)
            val jsonFile = File(vdir, "$versionId.json")
            if (!jsonFile.exists()) throw LauncherException("Файлы версии отсутствуют", versionId)

            val vj = Net.json.decodeFromString<VersionJson>(jsonFile.readText())
            // inheritsFrom (Fabric): базовый vanilla json даёт client.jar и ассеты
            val baseVj = vj.inheritsFrom?.let { inherit ->
                val bdir = File(Storage.versions(ctx), inherit)
                val bjson = File(bdir, "$inherit.json")
                if (bjson.exists()) Net.json.decodeFromString<VersionJson>(bjson.readText()) else null
            }

            _state.value = LaunchState.Preparing("Сборка аргументов")
            val javaExe = resolveOrInstallJava(javaPath, onLog)

            val classpath = buildClasspath(vj, baseVj)
            val nativesDir = Storage.natives(ctx, versionId)

            val args = mutableListOf<String>()
            args += javaExe
            // память
            args += "-Xms${inst.memoryMb / 2}M"
            args += "-Xmx${inst.memoryMb}M"
            // natives и system props
            args += "-Djava.library.path=${nativesDir.absolutePath}"
            args += "-Dminecraft.launcher.brand=nullLauncher-Mobile"
            args += "-Dminecraft.launcher.version=0.1.0"
            // JVM-аргументы из json (ос-правила: берём без os и linux)
            (baseVj?.arguments?.jvm ?: vj.arguments?.jvm ?: emptyList()).forEach { el ->
                if (!rulesAllow(el.rules)) return@forEach
                collectValues(el.value).forEach { v -> args += substitute(v, classpath, nativesDir, account) }
            }
            if (args.none { it.startsWith("-cp") || it.startsWith("-classpath") }) {
                args += "-cp"; args += classpath
            }
            // mainClass
            val mainClass = vj.mainClass.ifBlank { baseVj?.mainClass.orEmpty() }
            if (mainClass.isBlank()) throw LauncherException("mainClass не найден в json версии", versionId)
            args += mainClass
            // аргументы игры
            val gameDir = Storage.instanceDir(ctx, inst.id)
            (baseVj?.arguments?.minecraft ?: vj.arguments?.minecraft ?: emptyList()).forEach { el ->
                if (!rulesAllow(el.rules)) return@forEach
                collectValues(el.value).forEach { v ->
                    args += substitute(
                        v, classpath, nativesDir, account,
                        gameDir = gameDir,
                        assetIndex = baseVj?.assetIndex?.id ?: vj.assetIndex?.id ?: vj.assets,
                        versionId = versionId,
                    )
                }
            }

            val cmdLine = args.joinToString(" ")
            onLog?.invoke("> $cmdLine")
            Log.i(TAG, cmdLine)

            _state.value = LaunchState.Preparing("Запуск JVM")
            val pb = ProcessBuilder(args)
                .directory(gameDir)
                .redirectErrorStream(true)
            val proc = pb.start()
            process = proc

            // лог вывода игры — в файл и колбэк
            val logFile = File(Storage.logs(ctx), "game-${System.currentTimeMillis()}.log")
            val sb = StringBuilder()
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    sb.appendLine(line)
                    onLog?.invoke(line)
                }
            }
            logFile.writeText(sb.toString())

            val code = proc.waitFor()
            _state.value = if (code == 0) LaunchState.Stopped
            else LaunchState.Failed("Игра завершилась с ошибкой (код $code)", "Лог: ${logFile.name}")
        } catch (e: LauncherException) {
            _state.value = LaunchState.Failed(e.title, e.details)
            Log.e(TAG, "launch failed: ${e.title} ${e.details}", e)
        } catch (e: Exception) {
            _state.value = LaunchState.Failed("Сбой запуска", e.message ?: e.javaClass.simpleName)
            Log.e(TAG, "launch failed", e)
        }
    }

    fun stop() {
        process?.let {
            it.destroy()
            if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly()
        }
        process = null
        _state.value = LaunchState.Stopped
    }

    fun resetIdle() {
        if (_state.value !is LaunchState.Running) _state.value = LaunchState.Idle
    }

    // ---------- internals ----------

    /**
     * Путь к java: из настроек → авто-поиск в runtimes/ → **авто-установка**.
     * JRE скачивается из публичных сборок OpenJDK для Android с прогрессом на главной.
     */
    private suspend fun resolveOrInstallJava(javaPath: String?, onLog: ((String) -> Unit)?): String {
        resolveJava(javaPath)?.let { return it }

        val app = ctx.applicationContext as? org.nulllauncher.mobile.NullApp
            ?: throw LauncherException(
                "Java-окружение не найдено",
                "Установи Java Runtime в Настройках → Java Runtime",
            )

        onLog?.invoke("Java не найдена — скачиваю Java 17 для Android…")
        val path = JreInstaller.install(app) { stage, done, total ->
            app.installProgress.value = org.nulllauncher.mobile.InstallProgress(stage, done, total)
            _state.value = LaunchState.Preparing(stage)
        }
        app.installProgress.value = null
        app.settings.setJavaPath(path) // запоминаем, чтобы больше не спрашивать
        onLog?.invoke("Java 17 установлена: $path")
        return path
    }

    /** Путь к java: из настроек, либо авто-поиск в runtimes/ */
    private fun resolveJava(javaPath: String?): String? {
        javaPath?.let { if (File(it).canExecute() || File(it).exists()) return it }
        // авто-поиск: runtimes/<any>/bin/java[.exe]
        Storage.runtimes(ctx).listFiles()?.forEach { dir ->
            listOf("bin/java", "bin/java.exe", "java", "java.exe").forEach { rel ->
                val f = File(dir, rel)
                if (f.exists()) return f.absolutePath
            }
            // структура jre17-<arch>/bin/java (распаковка TarXz)
            TarXz.findJavaBinary(dir)?.let { return it.absolutePath }
        }
        return null
    }

    private fun buildClasspath(vararg vjs: VersionJson?): String {
        val entries = LinkedHashSet<String>()
        val list = vjs.filterNotNull()
        for (vj in list) {
            VersionManager(ctx).allLibraries(vj).forEach { lib ->
                val art = lib.downloads?.artifact ?: return@forEach
                val parts = lib.name.split(":")
                if (parts.size >= 3) {
                    val path = "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar"
                    entries += File(Storage.libraries(ctx), path).absolutePath
                }
            }
        }
        // client.jar базовой (vanilla) версии — последним в classpath при наследовании
        val base = list.last()
        val versionDir = File(Storage.versions(ctx), base.id)
        entries += File(versionDir, "${base.id}.jar").absolutePath
        return entries.joinToString(File.pathSeparator)
    }

    private fun rulesAllow(rules: List<org.nulllauncher.mobile.data.versions.Rule>?): Boolean {
        if (rules.isNullOrEmpty()) return true
        val osRules = rules.filter { it.os?.name != null }
        if (osRules.isEmpty()) return true
        return osRules.any { it.action == "allow" && it.os?.name == "linux" }
    }

    private fun collectValues(el: kotlinx.serialization.json.JsonElement?): List<String> = when (el) {
        null -> emptyList()
        is kotlinx.serialization.json.JsonArray -> el.mapNotNull {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
        is kotlinx.serialization.json.JsonPrimitive -> listOf(el.content)
        else -> emptyList()
    }

    /** ${library_name}, ${auth_player_name}, ${game_directory} и т.д. */
    private fun substitute(
        raw: String,
        classpath: String,
        nativesDir: File,
        account: Account?,
        gameDir: File = Storage.instanceDir(ctx, "default"),
        assetIndex: String? = null,
        versionId: String? = null,
    ): String {
        var s = raw
        fun rep(key: String, value: String) {
            s = s.replace("\${$key}", value)
        }
        rep("auth_player_name", account?.name ?: OFFLINE_NAME)
        rep("auth_uuid", account?.uuid ?: "0".repeat(32))
        rep("auth_access_token", account?.accessToken ?: "0".repeat(32))
        rep("auth_session", account?.accessToken ?: "0".repeat(32))
        rep("user_type", if (account != null) "msa" else "legacy")
        rep("user_properties", "{}")
        rep("version_name", versionId ?: "")
        rep("game_directory", gameDir.absolutePath)
        rep("game_assets", File(Storage.assets(ctx), "indexes/${assetIndex ?: ""}").absolutePath)
        rep("assets_root", Storage.assets(ctx).absolutePath)
        rep("assets_index_name", assetIndex ?: "")
        rep("natives_directory", nativesDir.absolutePath)
        rep("launcher_name", "nullLauncher-Mobile")
        rep("launcher_version", "0.1.0")
        rep("classpath", classpath)
        rep("classpath_separator", File.pathSeparator)
        rep("version_type", "release")
        return s
    }
}
