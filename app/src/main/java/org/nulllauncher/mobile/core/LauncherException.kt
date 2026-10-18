package org.nulllauncher.mobile.core

/** Единый тип ошибок: понятный заголовок + детали для логов */
class LauncherException(val title: String, val details: String = "", cause: Throwable? = null) :
    Exception("$title: $details", cause)
