package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.nulllauncher.mobile.InstallProgress
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.loaders.LoaderService
import org.nulllauncher.mobile.data.versions.ManifestVersion
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import org.nulllauncher.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Мастер создания сборки — 5 шагов, как в ПК-лаунчере:
 * 1 Шаблон и название · 2 Версия Minecraft · 3 Загрузчик · 4 Java и память · 5 Готово.
 * Кнопки «Назад/Далее» закреплены внизу и не уезжают за экран.
 */
@Composable
fun InstanceCreationWizard(app: NullApp, nav: NavController, onFinish: (Instance) -> Unit) {
    val scope = rememberCoroutineScope()
    val billing = app.billing
    val install by app.installProgress.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 5
    val stepTitles = listOf("Шаблон и название", "Версия Minecraft", "Загрузчик", "Java и память", "Готово")

    var maxMemoryMb by remember { mutableIntStateOf(4096) }
    var customMemory by remember { mutableStateOf("") }
    val maxAllowed = remember { app.settings.maxMemoryMb() }
    val totalRamMb = remember {
        val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (am != null) {
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            (info.totalMem / (1024 * 1024)).toInt()
        } else 0
    }

    var loader by remember { mutableStateOf("vanilla") }

    var versions by remember { mutableStateOf<List<ManifestVersion>>(emptyList()) }
    var versionQuery by remember { mutableStateOf("") }
    var selectedVersion by remember { mutableStateOf<ManifestVersion?>(null) }
    var filter by remember { mutableStateOf("release") } // all | release | old
    var javaReq by remember { mutableStateOf<Int?>(null) }

    var template by remember { mutableStateOf("clean") }
    var instanceName by remember { mutableStateOf("") }
    var instanceDescription by remember { mutableStateOf("") }
    var coverColor by remember { mutableStateOf("#3ecf8e") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val suggestedName = "${selectedVersion?.id ?: ""} ${loaderLabel(loader)}".trim()

    fun applyTemplate(id: String) {
        template = id
        loader = when (id) {
            "clean" -> "vanilla"
            "fabric_burst", "shaders" -> "fabric"
            "forge" -> "forge"
            "neoforge" -> "neoforge"
            "quilt" -> "quilt"
            else -> loader
        }
    }

    fun canProceedStep(): Boolean = when (step) {
        1 -> true
        2 -> selectedVersion != null
        3 -> LoaderService.isSupported(loader)
        4 -> maxMemoryMb in app.settings.minMemoryMb()..maxAllowed
        else -> true
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val all = app.versions.manifest().versions
                versions = all
                selectedVersion = all.firstOrNull { it.type == "release" } ?: all.firstOrNull()
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить версии"
            }
        }
    }

    LaunchedEffect(selectedVersion?.id) {
        javaReq = null
        val id = selectedVersion?.id ?: return@LaunchedEffect
        try {
            javaReq = app.versions.versionJson(id).javaVersion?.majorVersion
        } catch (_: Exception) {
        }
    }

    fun next() {
        if (step < totalSteps && canProceedStep()) step++
    }

    fun prev() {
        if (step > 1) step--
    }

    fun create() {
        if (step != totalSteps || !canProceedStep() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                if (!billing.isPremiumActive() && app.instances.list().size >= 3) {
                    throw LauncherException("Лимит сборок", "В бесплатной версии не более 3 сборок. Оформите Premium.")
                }
                val name = instanceName.trim().ifBlank { suggestedName }
                var created = app.instances.create(
                    name = name,
                    versionId = selectedVersion!!.id,
                    loader = loader,
                )
                created = created.copy(
                    memoryMb = maxMemoryMb,
                    description = instanceDescription.trim(),
                    colorHex = coverColor,
                )
                app.instances.update(created)

                // 1) клиент игры — без него «Играть» скажет «версия не установлена»
                if (!app.versions.isInstalled(created.versionId)) {
                    app.versions.install(created.versionId) { s, d, t ->
                        app.installProgress.value = InstallProgress(s, d, t)
                    }
                    app.logDownload("Minecraft ${created.versionId}", created.name, 0, true)
                }

                // 2) загрузчик поверх клиента
                if (LoaderService.isSupported(loader)) {
                    try {
                        val lv = LoaderService.latestLoader(loader)
                        LoaderService.installLoader(app, loader, created.versionId, lv.version) { s, d, t ->
                            app.installProgress.value = InstallProgress(s, d, t)
                        }
                        app.logDownload("Загрузчик $loader ${lv.version}", created.name, 0, true)
                    } catch (_: Exception) {
                    }
                }
                app.installProgress.value = null
                loading = false
                onFinish(created)
            } catch (e: Exception) {
                app.installProgress.value = null
                loading = false
                error = e.message ?: "Ошибка создания сборки"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Column(Modifier.weight(1f)) {
                Text("Новая сборка", style = MaterialTheme.typography.titleLarge)
                Text(
                    "У каждой сборки своя папка — моды, миры и настройки не смешиваются.",
                    fontSize = 11.sp, color = Mute,
                )
            }
            Text(
                "Шаг $step из $totalSteps",
                fontSize = 12.sp, color = Acc2, fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { step.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(6.dp)),
        )

        Text(
            stepTitles[step - 1],
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        // контент шага ограничен по высоте — кнопки внизу всегда видны
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                1 -> InstanceCreationWizardStep1Template(
                    template = template,
                    onTemplateChange = { applyTemplate(it) },
                    name = instanceName,
                    onNameChange = { instanceName = it },
                    description = instanceDescription,
                    onDescriptionChange = { instanceDescription = it },
                    color = coverColor,
                    onColorChange = { coverColor = it },
                    suggestedName = suggestedName,
                )

                2 -> VersionStep(
                    versions = versions,
                    query = versionQuery,
                    onQueryChange = { versionQuery = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    selected = selectedVersion,
                    onSelect = { selectedVersion = it },
                    javaReq = javaReq,
                )

                3 -> Column(Modifier.fillMaxSize()) {
                    InstanceCreationWizardStep2Loader(loader = loader, onSelect = { loader = it })
                    if (!LoaderService.isSupported(loader)) {
                        Text(
                            "⚠ ${loaderLabel(loader)} пока недоступен на Android — выберите Vanilla, Fabric или Quilt.",
                            fontSize = 11.sp, color = Warn,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                4 -> MemoryStep(
                    maxMemoryMb = maxMemoryMb,
                    onMemoryChange = { maxMemoryMb = it },
                    customMemory = customMemory,
                    onCustomChange = { customMemory = it },
                    maxAllowed = maxAllowed,
                    totalRamMb = totalRamMb,
                    minMemory = app.settings.minMemoryMb(),
                )

                else -> FinishStep(
                    app = app,
                    name = instanceName.trim().ifBlank { suggestedName },
                    version = selectedVersion?.id ?: "",
                    loader = loader,
                    memory = maxMemoryMb,
                    onCreate = { create() },
                    loading = loading,
                    error = error,
                    stage = install?.stage,
                )
            }
        }

        error?.let {
            Text(it, color = Err, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { prev() },
                    modifier = Modifier.width(110.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) { Text("Назад", color = Acc2) }
            } else {
                Box(Modifier.width(110.dp))
            }

            if (step < totalSteps) {
                Button(
                    onClick = { next() },
                    enabled = canProceedStep(),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                ) {
                    Text("Далее", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { create() },
                    enabled = canProceedStep() && !loading,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                ) {
                    Text(if (loading) "Создание…" else "Создать", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Шаг 4: память — чипы по ширине экрана (не вылезают), ручной ввод, лимиты устройства. */
@Composable
private fun MemoryStep(
    maxMemoryMb: Int,
    onMemoryChange: (Int) -> Unit,
    customMemory: String,
    onCustomChange: (String) -> Unit,
    maxAllowed: Int,
    totalRamMb: Int,
    minMemory: Int,
) {
    var applyError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Java и память", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Java устанавливается автоматически при первом запуске — версия подбирается под игру.",
            fontSize = 11.sp, color = Mute,
        )

        Spacer(Modifier.height(12.dp))
        Text("Максимум RAM (МБ)", fontSize = 12.sp, color = Mute)
        Spacer(Modifier.height(6.dp))

        // чипы в 4 ряда по ширине — числа не вылезают за экран
        val options = listOf(512, 1024, 2048, 4096, 6144, 8192).filter { it <= maxAllowed }
        options.chunked(4).forEach { rowOpts ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOpts.forEach { mb ->
                    val selected = maxMemoryMb == mb
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
                            .border(if (selected) 1.dp else 0.dp, Acc2, RoundedCornerShape(12.dp))
                            .clickable { onMemoryChange(mb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (mb >= 1024) "${mb / 1024} ГБ" else "$mb МБ",
                            fontSize = 12.sp,
                            color = if (selected) Acc2 else Mute,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(4 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customMemory,
                onValueChange = { onCustomChange(it); applyError = null },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text("Свой ввод (МБ)") },
                isError = applyError != null,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val v = customMemory.trim().toIntOrNull()
                    when {
                        v == null -> applyError = "Введите число"
                        v < minMemory -> applyError = "Минимум $minMemory МБ"
                        v > maxAllowed -> applyError = "Максимум $maxAllowed МБ"
                        else -> {
                            onMemoryChange(v)
                            onCustomChange("")
                            applyError = null
                        }
                    }
                },
                modifier = Modifier.width(96.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccSoft),
            ) { Text("Применить", fontSize = 11.sp, color = Acc2) }
        }

        applyError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 11.sp, color = Err)
        }

        Spacer(Modifier.height(10.dp))
        Text("Минимум: $minMemory МБ", fontSize = 11.5.sp, color = Mute)
        if (totalRamMb > 0) {
            Text("Всего RAM в системе: %,d МБ".format(totalRamMb), fontSize = 11.5.sp, color = Mute)
        }
        Text("Доступно выделить игре: до $maxAllowed МБ (75% RAM — защита от сбоя системы)", fontSize = 11.sp, color = Mute)
    }
}

/** Шаг 2: список версий MC — поиск, фильтры, дата выхода, требуемая Java у выбранной версии. */
@Composable
private fun VersionStep(
    versions: List<ManifestVersion>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    selected: ManifestVersion?,
    onSelect: (ManifestVersion) -> Unit,
    javaReq: Int?,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardChip("Релизы", filter == "release") { onFilterChange("release") }
            WizardChip("Все", filter == "all") { onFilterChange("all") }
            WizardChip("Старые", filter == "old") { onFilterChange("old") }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск версии…") },
            trailingIcon = { Icon(Icons.Filled.Search, null, tint = Mute, modifier = Modifier.size(18.dp)) },
        )

        Spacer(Modifier.height(8.dp))

        val shown = versions
            .filter {
                when (filter) {
                    "release" -> it.type == "release"
                    "old" -> it.type == "old_alpha" || it.type == "old_beta"
                    else -> true
                }
            }
            .filter { query.isBlank() || it.id.contains(query, ignoreCase = true) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(shown, key = { it.id }) { v ->
                val isSelected = selected?.id == v.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccSoft else MaterialTheme.colorScheme.surface)
                        .border(if (isSelected) 1.dp else 0.dp, Acc2, RoundedCornerShape(12.dp))
                        .clickable { onSelect(v) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.id, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (isSelected) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Check, null, tint = Acc2, modifier = Modifier.size(15.dp))
                            }
                        }
                        val date = fmtReleaseDate(v.releaseTime.ifBlank { v.time })
                        val typeLabel = when (v.type) {
                            "release" -> "релиз"
                            "snapshot" -> "снапшот"
                            else -> "старая"
                        }
                        val java = if (isSelected) javaReq?.let { " · Java $it" } ?: "" else ""
                        if (date.isNotEmpty() || java.isNotEmpty()) {
                            Text("$date · $typeLabel$java", fontSize = 10.5.sp, color = Mute)
                        }
                    }
                    Text(
                        when (v.type) {
                            "release" -> "release"
                            else -> v.type.replace("_", "-")
                        },
                        fontSize = 9.5.sp, color = Mute,
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FinishStep(
    app: NullApp,
    name: String,
    version: String,
    loader: String,
    memory: Int,
    onCreate: () -> Unit,
    loading: Boolean,
    error: String?,
    stage: String?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Готово к созданию", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AccSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.firstOrNull()?.uppercase() ?: "S", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Acc2)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Text("$version · ${loaderLabel(loader)}", fontSize = 11.sp, color = Mute)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCell("Версия", version)
            SummaryCell("Загрузчик", loaderLabel(loader))
            SummaryCell("Память", "${memory / 1024} ГБ")
        }

        Spacer(Modifier.height(14.dp))

        if (loading && stage != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(stage, fontSize = 11.sp, color = Acc2)
            Spacer(Modifier.height(8.dp))
        }

        error?.let {
            Text(it, fontSize = 12.sp, color = Err, maxLines = 4)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { onCreate() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc),
        ) {
            Text(
                if (loading) "Создание…" else "Создать сборку",
                color = Color.White, fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "После создания откроется карточка сборки — там запуск, моды, миры и ресурс-паки.",
            fontSize = 11.sp, color = Mute,
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Mute)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun WizardChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.dp else 0.dp, Acc2, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Acc2 else Mute,
        )
    }
}

/** "26.3" / "1.21.11" — локализованная дата выхода из ISO-строки Mojang. */
private fun fmtReleaseDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val inSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val date = try {
            inSdf.parse(iso)
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(iso)
        }
        if (date == null) "" else SimpleDateFormat("d MMM yyyy", Locale("ru")).format(date)
    } catch (_: Exception) {
        ""
    }
}

private fun loaderLabel(loader: String): String = when (loader) {
    "fabric" -> "Fabric"
    "forge" -> "Forge"
    "neoforge" -> "NeoForge"
    "quilt" -> "Quilt"
    else -> "Ваниль"
}
