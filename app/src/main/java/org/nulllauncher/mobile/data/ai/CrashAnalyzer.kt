package org.nulllauncher.mobile.data.ai

/**
 * Локальный анализатор вылетов: работает по правилам, офлайн и мгновенно.
 * Ничего не качает — ищет характерные строки в логе и отдаёт диагноз + действия.
 */

/** Действие, которое можно выполнить кнопкой прямо из диагноза/ответа ИИ */
sealed class CrashAction {
    /** Увеличить память сборки */
    data class SetRam(val instanceId: String, val mb: Int) : CrashAction()

    /** Установить мод из каталога (slug Modrinth) */
    data class InstallMod(val slug: String, val title: String) : CrashAction()

    /** Установить Java */
    object InstallJava : CrashAction()

    /** Войти через Microsoft */
    object Login : CrashAction()

    /** Открыть каталог */
    object OpenCatalog : CrashAction()

    /** Переустановить загрузчик/версию */
    object ReinstallVersion : CrashAction()
}

data class Diagnosis(
    val title: String,        // короткий диагноз
    val detail: String,       // объяснение простыми словами
    val actions: List<CrashAction>,
    val rule: String,         // id правила (для статистики)
)

object CrashAnalyzer {

    /**
     * Разобрать хвост лога. null = незнакомая ошибка (этот случай — за ИИ).
     * @param log текст лога (хвост, ~20КБ)
     * @param instanceId сборка, в которой упала (для действия «установить память»)
     */
    fun analyze(log: String, instanceId: String?, ramMb: Int = 2048): Diagnosis? {
        val l = log.takeLast(60_000)

        // ---------- память ----------
        if (l.contains("OutOfMemoryError") || l.contains("Java heap space") ||
            l.contains("GC overhead limit exceeded")
        ) {
            val target = (ramMb * 3 / 2).coerceAtLeast(ramMb + 1024)
            return Diagnosis(
                title = "Закончилась память JVM",
                detail = "Игре не хватило выделенной RAM (${ramMb / 1024} ГБ). " +
                    "Обычно помогает поднять лимит до ${target / 1024} ГБ — если на телефоне столько есть.",
                actions = listOfNotNull(
                    instanceId?.let { CrashAction.SetRam(it, target) },
                    CrashAction.OpenCatalog,
                ),
                rule = "oom",
            )
        }

        // ---------- не установлен мод (класс не найден) ----------
        if (l.contains("ClassNotFoundException") || l.contains("NoClassDefFoundError")) {
            val cls = Regex("(?:ClassNotFoundException|NoClassDefFoundError)[^\\n]*?([\\w.$]{6,})")
                .find(l)?.groupValues?.get(1)?.substringAfterLast('.')
            return Diagnosis(
                title = "Не хватает мода или библиотеки",
                detail = "Игра не нашла класс${cls?.let { " $it" } ?: ""} — скорее всего, не установлен " +
                    "зависимый мод (часто это Fabric API, Cloth Config или мод, от которого зависит другой).",
                actions = listOf(CrashAction.InstallMod("fabric-api", "Fabric API"), CrashAction.OpenCatalog),
                rule = "classnotfound",
            )
        }

        // ---------- Fabric API отсутствует ----------
        if (l.contains("fabric-api", ignoreCase = true) || l.contains("fabricapi", ignoreCase = true) ||
            (l.contains("fabric", ignoreCase = true) && l.contains("requires", ignoreCase = true))
        ) {
            return Diagnosis(
                title = "Сборке нужен Fabric API",
                detail = "Моды для Fabric требуют библиотеку Fabric API — её часто забывают ставить.",
                actions = listOf(CrashAction.InstallMod("fabric-api", "Fabric API")),
                rule = "fabricapi",
            )
        }

        // ---------- дубликат мода / конфликт ----------
        if (l.contains("Duplicate mod", ignoreCase = true) || l.contains("Mod file", ignoreCase = true) && l.contains("already", ignoreCase = true)) {
            return Diagnosis(
                title = "Два мода конфликтуют",
                detail = "В папке mods лежат две копии одного мода (обычно после обновления). " +
                    "Удали старую версию в карточке сборки.",
                actions = listOf(CrashAction.OpenCatalog),
                rule = "duplicate",
            )
        }

        // ---------- Java ----------
        if (l.contains("Unable to launch JVM") || l.contains("Cannot find JVM") ||
            l.contains("libjvm") || l.contains("Java binary") || l.contains("error=2,")
        ) {
            return Diagnosis(
                title = "Проблема с Java",
                detail = "Java-окружение не запустилось или не найдено. Переустановка обычно решает.",
                actions = listOf(CrashAction.InstallJava),
                rule = "jvm",
            )
        }

        // ---------- токен/аккаунт ----------
        if (l.contains("401") || l.contains("403") || l.contains("Invalid credentials") ||
            l.contains("access token", ignoreCase = true) && l.contains("expired", ignoreCase = true)
        ) {
            return Diagnosis(
                title = "Сессия истекла",
                detail = "Токен аккаунта устарел — нужно перелогиниться, это нормально.",
                actions = listOf(CrashAction.Login),
                rule = "auth",
            )
        }

        // ---------- версия не установлена ----------
        if (l.contains("not installed") || l.contains("No such file") && l.contains(".jar")) {
            return Diagnosis(
                title = "Файлы игры не найдены",
                detail = "Клиент или библиотеки не установлены (или повреждены). Переустанови версию в менеджере версий.",
                actions = listOf(CrashAction.ReinstallVersion),
                rule = "notinstalled",
            )
        }

        // ---------- нативники / графика ----------
        if (l.contains("UnsatisfiedLinkError") || l.contains("no such library") ||
            l.contains("glfw", ignoreCase = true) && l.contains("failed", ignoreCase = true)
        ) {
            return Diagnosis(
                title = "Проблема с графикой/нативниками",
                detail = "Библиотеки подачи видео не загрузились. Попробуй переустановить версию — " +
                    "если не поможет, устройство может не поддерживать эту версию OpenGL.",
                actions = listOf(CrashAction.ReinstallVersion),
                rule = "natives",
            )
        }

        return null // незнакомая ошибка — здесь поможет только ИИ
    }
}
