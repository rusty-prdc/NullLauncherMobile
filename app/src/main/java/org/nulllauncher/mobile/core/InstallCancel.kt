package org.nulllauncher.mobile.core

/**
 * Флаг отмены текущей установки (версии или загрузчика).
 * UI ставит true по кнопке «Отмена», установщики проверяют на каждом шаге.
 */
object InstallCancel {
    @Volatile
    var requested: Boolean = false

    fun begin() { requested = false }
}
