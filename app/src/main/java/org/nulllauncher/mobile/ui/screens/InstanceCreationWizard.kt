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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.nulllauncher.mobile.core.Storage
import org.nulllauncher.mobile.data.billing.SubscriptionManager
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.data.loaders.LoaderService
import org.nulllauncher.mobile.data.versions.ManifestVersion
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Card2
import org.nulllauncher.mobile.ui.theme.Err
import org.nulllauncher.mobile.ui.theme.ErrSoft
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute
import org.nulllauncher.mobile.ui.theme.Ok
import org.nulllauncher.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstanceCreationWizard(app: NullApp, nav: NavController, onFinish: (Instance) -> Unit) {
    val scope = rememberCoroutineScope()
    val billing = app.billing

    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 5

    var maxMemoryMb by remember { mutableIntStateOf(4096) }
    var customMemory by remember { mutableStateOf("") }

    var loader by remember { mutableStateOf("vanilla") }

    var versions by remember { mutableStateOf<List<ManifestVersion>>(emptyList()) }
    var versionQuery by remember { mutableStateOf("") }
    var selectedVersion by remember { mutableStateOf<ManifestVersion?>(null) }
    var filter by remember { mutableStateOf("release") }

    var template by remember { mutableStateOf("clean") }
    var instanceName by remember { mutableStateOf("") }
    var coverColor by remember { mutableStateOf("#3ecf8e") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun canProceedStep(): Boolean = when (step) {
        1 -> true
        2 -> true
        3 -> selectedVersion != null
        4 -> instanceName.isNotBlank()
        else -> false
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val manifest = app.versions.manifest()
                versions = manifest.versions.filter {
                    when (filter) {
                        "release" -> it.type == "release"
                        "snapshot" -> it.type == "snapshot"
                        else -> true
                    }
                }
                if (versions.isNotEmpty()) selectedVersion = versions.first()
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить версии"
            }
        }
    }

    fun next() {
        if (step < totalSteps && canProceedStep()) step++
    }

    fun prev() {
        if (step > 1) step--
    }

    fun create() {
        val step1Ok = true
        val step2Ok = true
        val step3Ok = selectedVersion != null
        val step4Ok = instanceName.isNotBlank()
        if (step != totalSteps || !step1Ok || !step2Ok || !step3Ok || !step4Ok) return
        loading = true
        error = null
        scope.launch {
            try {
                if (!billing.isPremiumActive() && app.instances.list().size >= 3) {
                    throw LauncherException("Лимит сборок", "В бесплатной версии не более 3 сборок. Оформите Premium.")
                }
                val created = app.instances.create(
                    name = instanceName.trim(),
                    versionId = selectedVersion!!.id,
                    loader = loader,
                )
                app.instances.update(created.copy(memoryMb = maxMemoryMb))
                val inst = created
                if (loader != "vanilla") {
                    try {
                        val lv = LoaderService.latestLoader(loader)
                        LoaderService.installLoader(app, loader, inst.versionId, lv.version) { s, d, t ->
                            app.installProgress.value = InstallProgress(s, d, t)
                        }
                    } catch (e: Exception) {
                    }
                }
                app.installProgress.value = null
                loading = false
                onFinish(inst)
            } catch (e: Exception) {
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
            Text("Шаг $step из $totalSteps", fontSize = 12.sp, color = Acc2, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { step.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(6.dp)),
        )

        Spacer(Modifier.height(16.dp))

        when (step) {
            1 -> JavaMemoryStep()
            2 -> LoaderStep(loader = loader, onSelect = { loader = it })
            3 -> VersionStep(
                versions = versions,
                query = versionQuery,
                onQueryChange = { versionQuery = it },
                filter = filter,
                onFilterChange = { filter = it },
                selected = selectedVersion,
                onSelect = { selectedVersion = it },
            )
            4 -> TemplateStep(
                template = template,
                onTemplateChange = { template = it },
                name = instanceName,
                onNameChange = { instanceName = it },
                color = coverColor,
                onColorChange = { coverColor = it },
            )
            else -> FinishStep(
                app = app,
                name = instanceName,
                version = selectedVersion?.id ?: "",
                loader = loader,
                memory = maxMemoryMb,
                onCreate = { create() },
                loading = loading,
                error = error,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                ) {
                    Text("Далее", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { create() },
                    enabled = canProceedStep() && !loading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Acc),
                ) {
                    if (loading) {
                        Text("Создание…", color = Color.White)
                    } else {
                        Text("Создать", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Err, fontSize = 12.sp, maxLines = 3)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun JavaMemoryStep() {
    var maxMemoryMb by remember { mutableIntStateOf(4096) }
    var customMemory by remember { mutableStateOf("") }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Java и память", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text("Максимум RAM (МБ)", fontSize = 12.sp, color = Mute)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1024, 2048, 4096, 6144, 8192, 12288, 16384).forEach { mb ->
                val selected = maxMemoryMb == mb
                Box(
                    Modifier
                        .size(64.dp, 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
                        .border(if (selected) 1.dp else 0.dp, Acc2, RoundedCornerShape(12.dp))
                        .clickable { maxMemoryMb = mb },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${mb / 1024} ГБ", fontSize = 12.sp, color = if (selected) Acc2 else Mute)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customMemory,
                onValueChange = { customMemory = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text("Свой ввод (МБ)") },
            )
            Button(
                onClick = {
                    val v = customMemory.trim().toIntOrNull() ?: return@Button
                    maxMemoryMb = v
                    customMemory = ""
                },
                modifier = Modifier.width(90.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccSoft),
            ) {
                Text("Применить", fontSize = 11.sp, color = Acc2)
            }
        }
    }
}

@Composable
private fun LoaderStep(loader: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "vanilla" to "Ваниль",
        "fabric" to "Fabric",
        "forge" to "Forge",
        "neoforge" to "NeoForge",
        "quilt" to "Quilt",
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Загрузчик модов", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { (id, label) ->
                val selected = loader == id
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
                        .border(if (selected) 2.dp else 0.dp, Acc2, RoundedCornerShape(16.dp))
                        .clickable { onSelect(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, color = if (selected) Acc2 else Mute, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Ваниль не поддерживает моды. Для модов выберите Fabric, Forge, NeoForge или Quilt.",
            fontSize = 11.sp, color = Mute,
        )
    }
}

@Composable
private fun VersionStep(
    versions: List<ManifestVersion>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    selected: ManifestVersion?,
    onSelect: (ManifestVersion) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Версия Minecraft", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardChip("Все", filter == "all") { onFilterChange("all") }
            WizardChip("Релизы", filter == "release") { onFilterChange("release") }
            WizardChip("Снапшоты", filter == "snapshot") { onFilterChange("snapshot") }
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

        Spacer(Modifier.height(12.dp))

        val shown = versions
            .filter {
                when (filter) {
                    "release" -> it.type == "release"
                    "snapshot" -> it.type == "snapshot"
                    else -> true
                }
            }
            .filter { query.isBlank() || it.id.contains(query, ignoreCase = true) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.id }) { v ->
                val isSelected = selected?.id == v.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccSoft else MaterialTheme.colorScheme.surface)
                        .border(if (isSelected) 1.dp else 0.dp, Acc2, RoundedCornerShape(12.dp))
                        .clickable { onSelect(v) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(v.id, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (v.type == "release") "Релиз" else "Снапшот",
                        fontSize = 10.sp, color = Mute,
                    )
                    if (isSelected) Icon(Icons.Filled.Check, null, tint = Acc2, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TemplateStep(
    template: String,
    onTemplateChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    color: String,
    onColorChange: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Шаблон и название", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val templates = listOf(
                "clean" to "Чистый",
                "fabric_burst" to "Fabric + Bust FPS",
                "shaders" to "Шейдеры",
                "forge" to "Forge",
                "neoforge" to "NeoForge",
                "quilt" to "Quilt",
            )
            templates.forEach { (id, label) ->
                val selected = template == id
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
                        .border(if (selected) 2.dp else 0.dp, Acc2, RoundedCornerShape(16.dp))
                        .clickable { onTemplateChange(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 11.5.sp, color = if (selected) Acc2 else Mute, maxLines = 2)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название") },
            placeholder = { Text("например, 26.3 Fabric") },
        )

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Цвет иконки", fontSize = 12.sp, color = Mute)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(android.graphics.Color.parseColor(color))),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 10.sp, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onColorChange("#3ecf8e") },
                modifier = Modifier.width(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(color))),
            ) {
                Text("Зелёный", fontSize = 11.sp, color = Color.White)
            }
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
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
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
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${loaderLabel(loader)} $version · ${memory / 1024} ГБ RAM", fontSize = 11.sp, color = Mute)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Макс. RAM", fontSize = 11.sp, color = Mute)
                Text("${memory / 1024} ГБ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text("Загрузчик", fontSize = 11.sp, color = Mute)
                Text(loaderLabel(loader), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text("Версия", fontSize = 11.sp, color = Mute)
                Text(version, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onCreate() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc),
        ) {
            if (loading) {
                Text("Создание…", color = Color.White, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Создать сборку", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
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

private fun loaderLabel(loader: String): String = when (loader) {
    "fabric" -> "Fabric"
    "forge" -> "Forge"
    "neoforge" -> "NeoForge"
    "quilt" -> "Quilt"
    else -> "Ваниль"
}