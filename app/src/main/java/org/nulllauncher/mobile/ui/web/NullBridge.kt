package org.nulllauncher.mobile.ui.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.loaders.LoaderService
import org.nulllauncher.mobile.data.modrinth.ModrinthService
import org.nulllauncher.mobile.data.runtime.JreInstaller
import org.nulllauncher.mobile.data.versions.VersionManager
import org.nulllauncher.mobile.ui.screens.loaderLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JavascriptInterface для HTML-интерфейса (assets/ui/index.html).
 * Все методы вызываются с binder-потока WebView — диск и сеть разрешены,
 * UI-операции выполняются через mainHandler.
 */
class NullBridge(private val app: NullApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Вызывается из HTML-профиля: переход в классический Compose-интерфейс */
    var onClassic: (() -> Unit)? = null

    @JavascriptInterface
    fun openClassic() {
        mainHandler.post { onClassic?.invoke() }
    }

    /** Начальное состояние прогрессов (версия/Java/запуск) для HTML */
    @JavascriptInterface
    fun progressInfo(): String {
        val state = app.launcher.state.value
        val launch = JSONObject().apply {
            when (state) {
                is org.nulllauncher.mobile.data.launch.LaunchState.Preparing -> {
                    put("state", "preparing"); put("stage", state.stage)
                }
                is org.nulllauncher.mobile.data.launch.LaunchState.Running -> {
                    put("state", "running"); put("stage", "")
                }
                is org.nulllauncher.mobile.data.launch.LaunchState.Failed -> {
                    put("state", "failed"); put("stage", state.title); put("details", state.details)
                }
                org.nulllauncher.mobile.data.launch.LaunchState.Stopped -> put("state", "stopped")
                else -> put("state", "idle")
            }
        }
        val p = app.installProgress.value
        val install = if (p == null) JSONObject().put("on", false) else JSONObject()
            .put("on", true).put("stage", p.stage).put("done", p.done).put("total", p.total)
        val busy = JreInstaller.busyMajor.value
        val java = JSONObject()
            .put("major", busy ?: -1)
            .put("stage", JreInstaller.busyStage.value)
            .put("progress", JreInstaller.busyProgress.value)
        return JSONObject()
            .put("launch", launch)
            .put("install", install)
            .put("java", java)
            .toString()
    }

    private fun instJson(inst: Instance): JSONObject = JSONObject()
        .put("id", inst.id)
        .put("name", inst.name)
        .put("versionId", inst.versionId)
        .put("loader", inst.loader)
        .put("loaderLabel", loaderLabel(inst))
        .put("loaderVersion", inst.loaderVersion ?: "")
        .put("memoryMb", inst.memoryMb)
        .put("mods", app.modCount(inst.id))
        .put("installed", app.versions.isInstalled(inst.versionId))
        .put("lastPlayedAt", inst.lastPlayedAt)
        .put("coverEmoji", inst.coverEmoji)
        .put("colorHex", inst.colorHex)

    // ---------- состояние ----------

    @JavascriptInterface
    fun home(): String {
        val acc = app.currentAccount()
        val instances = app.instances.list()
        val lastId = run {
            var id: String? = null
            kotlin.runCatching {
                kotlinx.coroutines.runBlocking { app.settings.currentLastInstanceId() }
            }.onSuccess { id = it }
            id
        }
        return JSONObject()
            .put("account", acc?.let {
                JSONObject().put("name", it.name)
                    .put("type", if (it.accessToken.isBlank()) "offline" else "microsoft")
            } ?: JSONObject.NULL)
            .put("instances", JSONArray(instances.map { instJson(it) }))
            .put("lastInstanceId", lastId ?: JSONObject.NULL)
            .put("freeLimit", 3)
            .put("versionsInstalled", app.versionsCount())
            .toString()
    }

    @JavascriptInterface
    fun setLastInstance(id: String) {
        scope.launch { app.settings.setLastInstance(id) }
    }

    // ---------- запуск игры ----------

    @JavascriptInterface
    fun launch(instanceId: String): String {
        val inst = app.instances.get(instanceId)
            ?: return err("Сборка не найдена")
        val check = app.launcher.checkInstance(inst)
        if (check != null) return err(check)
        app.lastError.value = null
        scope.launch {
            app.settings.setLastInstance(inst.id)
            app.instances.markPlayed(inst.id)
            app.launcher.start(inst, app.currentAccount(), app.settings.currentJavaPath()) { l ->
                app.appendLog(l)
            }
        }
        return ok()
    }

    @JavascriptInterface
    fun stopGame() {
        app.launcher.stop()
    }

    // ---------- версии ----------

    @JavascriptInterface
    fun versions(filter: String): String = runBlockingJson {
        val m = app.versions.manifest()
        val arr = JSONArray()
        m.versions
            .filter { v ->
                when (filter) {
                    "release" -> v.type == "release"
                    "old" -> v.type == "old_alpha" || v.type == "old_beta"
                    else -> true
                }
            }
            .take(120)
            .forEach { v ->
                arr.put(
                    JSONObject()
                        .put("id", v.id)
                        .put("type", v.type)
                        .put("installed", app.versions.isInstalled(v.id))
                )
            }
        JSONObject().put("versions", arr).toString()
    }

    @JavascriptInterface
    fun installVersion(versionId: String): String {
        scope.launch {
            try {
                app.versions.install(versionId) { s, d, t ->
                    app.installProgress.value = org.nulllauncher.mobile.InstallProgress(s, d, t)
                }
                app.installProgress.value = null
                app.logDownload("Minecraft $versionId", "Версия", 0, true)
            } catch (e: Exception) {
                app.installProgress.value = null
                app.lastError.value = e.message ?: "Ошибка установки версии"
            }
        }
        return ok()
    }

    // ---------- сборки ----------

    @JavascriptInterface
    fun createInstance(name: String, versionId: String, loader: String): String {
        val freeLimit = 3
        if (app.instances.list().size >= freeLimit) {
            return err("Лимит 3 сборки в бесплатной версии. Оформите Premium.")
        }
        return try {
            val inst = app.instances.create(
                name = name.ifBlank { "Сборка $versionId" },
                versionId = versionId,
                loader = loader,
            )
            ok(JSONObject().put("id", inst.id))
        } catch (e: Exception) {
            err(e.message ?: "Ошибка создания сборки")
        }
    }

    @JavascriptInterface
    fun renameInstance(id: String, name: String) {
        val inst = app.instances.get(id) ?: return
        app.instances.update(inst.copy(name = name.ifBlank { inst.name }))
    }

    @JavascriptInterface
    fun deleteInstance(id: String) {
        app.instances.delete(id)
    }

    @JavascriptInterface
    fun setInstanceMemory(id: String, memoryMb: Int) {
        val inst = app.instances.get(id) ?: return
        val max = app.settings.maxMemoryMb()
        app.instances.update(inst.copy(memoryMb = memoryMb.coerceIn(app.settings.minMemoryMb(), max)))
    }

    @JavascriptInterface
    fun installLoaderFor(instanceId: String): String {
        val inst = app.instances.get(instanceId) ?: return err("Сборка не найдена")
        if (inst.loader == "vanilla") return ok()
        if (!LoaderService.isSupported(inst.loader)) {
            return err("Установка ${inst.loader} пока не реализована — выбери Fabric или оставь Vanilla")
        }
        scope.launch {
            try {
                val lv = LoaderService.latestLoader(inst.loader)
                LoaderService.installLoader(app, inst.loader, inst.versionId, lv.version) { s, d, t ->
                    app.installProgress.value = org.nulllauncher.mobile.InstallProgress(s, d, t)
                }
                app.installProgress.value = null
                app.logDownload("Загрузчик ${inst.loader} ${lv.version}", inst.name, 0, true)
            } catch (e: Exception) {
                app.installProgress.value = null
                app.lastError.value = e.message ?: "Ошибка установки загрузчика"
            }
        }
        return ok()
    }

    // ---------- каталог Modrinth ----------

    @JavascriptInterface
    fun searchMods(query: String, type: String, instanceId: String): String = runBlockingJson {
        val target = app.instances.get(instanceId)
        val res = ModrinthService.search(
            query = query,
            gameVersion = target?.versionId,
            loader = target?.loader?.takeIf { it != "vanilla" },
            projectType = type,
        )
        val arr = JSONArray()
        res.hits.forEach { h ->
            arr.put(
                JSONObject()
                    .put("projectId", h.projectId)
                    .put("slug", h.slug)
                    .put("title", h.title)
                    .put("author", h.author)
                    .put("downloads", h.downloads)
                    .put("iconUrl", h.iconUrl ?: JSONObject.NULL)
                    .put("description", h.description ?: "")
            )
        }
        JSONObject().put("hits", arr).toString()
    }

    @JavascriptInterface
    fun installMod(projectId: String, instanceId: String, type: String): String {
        val target = app.instances.get(instanceId) ?: return err("Сборка не найдена")
        scope.launch {
            try {
                val ver = ModrinthService.bestVersion(projectId, target.versionId, target.loader)
                    ?: throw org.nulllauncher.mobile.core.LauncherException(
                        "Нет версии для ${target.versionId} · ${loaderLabel(target)}", projectId
                    )
                val file = ver.files.firstOrNull { f -> f.primary } ?: ver.files.firstOrNull()
                    ?: throw org.nulllauncher.mobile.core.LauncherException("В версии нет файла", ver.name)
                val dest = when (type) {
                    "resourcepack" -> org.nulllauncher.mobile.core.Storage.instanceResourcePacks(app, target.id)
                    "shaderpack" -> org.nulllauncher.mobile.core.Storage.instanceShaderPacks(app, target.id)
                    "datapack" -> org.nulllauncher.mobile.core.Storage.instanceDataPacks(app, target.id)
                    else -> org.nulllauncher.mobile.core.Storage.instanceMods(app, target.id)
                }
                ModrinthService.installFile(file, dest) { d, tg ->
                    app.installProgress.value = org.nulllauncher.mobile.InstallProgress("Скачивание ${file.filename}", d, tg)
                }
                app.installProgress.value = null
                app.logDownload(ver.name, "${target.name} · $type", file.size, true)
            } catch (e: Exception) {
                app.installProgress.value = null
                app.lastError.value = e.message ?: "Ошибка установки мода"
            }
        }
        return ok()
    }

    // ---------- загрузки ----------

    @JavascriptInterface
    fun downloads(): String {
        val arr = JSONArray()
        app.downloads.value.take(30).forEach { r ->
            arr.put(
                JSONObject()
                    .put("name", r.name)
                    .put("detail", r.detail)
                    .put("size", r.size)
                    .put("at", r.at)
                    .put("ok", r.ok)
            )
        }
        return JSONObject().put("records", arr).toString()
    }

    // ---------- Java runtime ----------

    @JavascriptInterface
    fun javaInfo(): String {
        val installed = JSONArray(JreInstaller.installedMajors(app))
        val supported = JSONArray(JreInstaller.SUPPORTED)
        return JSONObject().put("installed", installed).put("supported", supported).toString()
    }

    @JavascriptInterface
    fun installJava(major: Int) {
        JreInstaller.installShared(app, major)
    }

    // ---------- аккаунты ----------

    @JavascriptInterface
    fun addOffline(name: String): String = try {
        app.accounts.addOffline(name)
        ok()
    } catch (e: Exception) {
        err(e.message ?: "Ошибка создания аккаунта")
    }

    @JavascriptInterface
    fun removeAccount() {
        app.accounts.accounts().firstOrNull()?.let { app.accounts.remove(it.uuid) }
    }

    // ---------- тема ----------

    @JavascriptInterface
    fun themeMode(): String = runBlockingJson {
        JSONObject().put("mode", app.settings.currentThemeMode()).toString()
    }

    @JavascriptInterface
    fun setThemeMode(mode: String) {
        scope.launch { app.settings.setThemeMode(mode) }
    }

    // ---------- утилиты ----------

    private fun ok(data: JSONObject = JSONObject()): String =
        JSONObject().put("ok", true).put("data", data).toString()

    private fun err(message: String): String =
        JSONObject().put("ok", false).put("error", message).toString()

    private fun runBlockingJson(block: suspend () -> String): String = try {
        kotlinx.coroutines.runBlocking { block() }
    } catch (e: Exception) {
        err(e.message ?: "Ошибка")
    }
}
