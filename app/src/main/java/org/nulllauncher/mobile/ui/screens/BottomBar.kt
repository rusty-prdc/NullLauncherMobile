package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nulllauncher.mobile.ui.theme.Acc

private data class Tab(val route: String, val title: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("instances", "Сборки", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    Tab("catalog", "Каталог", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    Tab("downloads", "Загрузки", Icons.Filled.Download, Icons.Outlined.Download),
    Tab("profile", "Профиль", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun BottomBar(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .95f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(tab.route) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (selected) tab.on else tab.off,
                    contentDescription = tab.title,
                    tint = if (selected) Acc else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp),
                )
                Text(
                    tab.title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Acc else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
