package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.AccSoft
import org.nulllauncher.mobile.ui.theme.Mute

@Composable
fun InstanceCreationWizardStep2Loader(loader: String, onSelect: (String) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Text(
      text = "Шаг 2. Загрузчик",
      fontSize = 15.sp,
      color = Mute,
      modifier = Modifier.padding(bottom = 6.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      val items = listOf(
        "vanilla" to "Ваниль",
        "fabric" to "Fabric",
        "forge" to "Forge",
        "neoforge" to "NeoForge",
        "quilt" to "Quilt"
      )
      items.forEach { (id, label) ->
        val selected = loader == id
        Box(
          modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AccSoft else MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 0.dp, Acc, RoundedCornerShape(16.dp))
            .clickable { onSelect(id) },
          contentAlignment = Alignment.Center
        ) {
          Text(label, fontSize = 13.sp, color = if (selected) Acc else Mute, )
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "Ваниль не поддерживает моды. Для модов выберите Fabric, Forge, NeoForge или Quilt.",
      fontSize = 11.sp, color = Mute
    )
  }
}
