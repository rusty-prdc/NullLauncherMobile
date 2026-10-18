package org.nulllauncher.mobile.data.touch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.nulllauncher.mobile.core.Net
import java.io.File

/**
 * Сенсорное управление (бесплатно для всех):
 * элементы оверлея с позициями в % от экрана — переживают поворот и разрешения.
 * Профили хранятся в папке сборки: touch_profiles.json
 */
@Serializable
data class TouchElement(
    val id: String = "",
    /** button = клавиша (space, shift, e…), joystick = левый стик, swipe = камера */
    val type: String = "button",
    /** клавиша для type=button: "key.space", "key.shift", "key.mouse.left"… */
    val key: String = "",
    val label: String = "",
    /** центр в процентах экрана (0..100) */
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    /** размер в % меньшей стороны экрана */
    val sizePct: Float = 10f,
    val opacity: Float = 0.85f,
)

@Serializable
data class TouchProfile(
    val id: String = "classic",
    val name: String = "Классика",
    @SerialName("elements") val elements: List<TouchElement> = emptyList(),
)

object TouchProfiles {

    /** Пресеты «Классика / PvE / Стройка» — как в хороших мобильных лаунчерах */
    fun presets(): List<TouchProfile> = listOf(
        TouchProfile(
            id = "classic",
            name = "Классика",
            elements = defaultElements + defaultButtons,
        ),
        TouchProfile(
            id = "pvp",
            name = "PvE",
            elements = listOf(
                TouchElement("joy", "joystick", "", "Движение", 18f, 72f, 26f),
                TouchElement("swipe", "swipe", "", "Камера", 62f, 45f, 55f, 0f),
                TouchElement("atk", "button", "key.mouse.left", "⚔", 84f, 62f, 13f),
                TouchElement("jump", "button", "key.space", "▲", 84f, 84f, 12f),
                TouchElement("sneak", "button", "key.shift", "▼", 68f, 84f, 9f),
                TouchElement("use", "button", "key.mouse.right", "✋", 68f, 66f, 10f),
            ),
        ),
        TouchProfile(
            id = "build",
            name = "Стройка",
            elements = listOf(
                TouchElement("joy", "joystick", "", "Движение", 16f, 74f, 24f),
                TouchElement("swipe", "swipe", "", "Камера", 60f, 42f, 55f, 0f),
                TouchElement("use", "button", "key.mouse.right", "✋", 84f, 60f, 13f),
                TouchElement("pick", "button", "key.mouse.middle", "⌖", 70f, 60f, 10f),
                TouchElement("inv", "button", "key.e", "E", 84f, 82f, 11f),
                TouchElement("jump", "button", "key.space", "▲", 68f, 84f, 11f),
                TouchElement("flyup", "button", "key.space", "⤒", 55f, 70f, 9f),
                TouchElement("flydn", "button", "key.shift", "⤓", 55f, 84f, 9f),
            ),
        ),
    )

    // фиксированные конструкторы (Kotlin-скриптовая запись ниже)
    private val defaultElements get() = listOf(
        TouchElement("joy", "joystick", "", "Движение", 18f, 72f, 26f),
        TouchElement("swipe", "swipe", "", "Камера", 60f, 45f, 55f, 0f),
    )
    private val defaultButtons get() = listOf(
        TouchElement("jump", "button", "key.space", "▲", 84f, 84f, 12f),
        TouchElement("sneak", "button", "key.shift", "▼", 68f, 84f, 9f),
        TouchElement("atk", "button", "key.mouse.left", "⚔", 84f, 62f, 12f),
        TouchElement("use", "button", "key.mouse.right", "✋", 68f, 66f, 10f),
        TouchElement("inv", "button", "key.e", "E", 55f, 84f, 9f),
        TouchElement("drop", "button", "key.q", "Q", 55f, 72f, 8f),
        TouchElement("esc", "button", "key.esc", "☰", 50f, 8f, 8f),
    )

    private fun file(app: android.content.Context, instanceId: String) =
        File(org.nulllauncher.mobile.core.Storage.instanceDir(app, instanceId), "touch_profiles.json")

    fun load(app: android.content.Context, instanceId: String): List<TouchProfile> {
        val f = file(app, instanceId)
        if (!f.exists()) return presets()
        return try {
            Net.json.decodeFromString(f.readText())
        } catch (_: Exception) {
            presets()
        }
    }

    fun save(app: android.content.Context, instanceId: String, profiles: List<TouchProfile>) {
        file(app, instanceId).writeText(Net.json.encodeToString(profiles))
    }

    /** Активный профиль сборки */
    fun active(app: android.content.Context, instanceId: String): TouchProfile =
        load(app, instanceId).firstOrNull() ?: presets().first()
}