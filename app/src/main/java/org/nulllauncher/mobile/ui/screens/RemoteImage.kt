package org.nulllauncher.mobile.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nulllauncher.mobile.core.Net
import org.nulllauncher.mobile.ui.theme.Card2

/**
 * Лёгкая загрузка картинок по URL (иконки Modrinth) без сторонних библиотек:
 * OkHttp из core.Net + BitmapFactory. Пока грузится — пустая плашка.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        if (url.isNullOrBlank()) return@produceState
        value = try {
            val bytes = withContext(Dispatchers.IO) { Net.getBytes(url) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val bm = bitmap
    if (bm != null) {
        Image(bitmap = bm, contentDescription = contentDescription, modifier = modifier)
    } else {
        Box(modifier.background(Card2))
    }
}
