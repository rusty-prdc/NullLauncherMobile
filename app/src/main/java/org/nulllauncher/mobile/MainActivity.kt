package org.nulllauncher.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import org.nulllauncher.mobile.ui.screens.AIScreen
import org.nulllauncher.mobile.ui.screens.BottomBar
import org.nulllauncher.mobile.ui.screens.CatalogScreen
import org.nulllauncher.mobile.ui.screens.HomeScreen
import org.nulllauncher.mobile.ui.screens.InstanceCreationWizard
import org.nulllauncher.mobile.ui.screens.InstanceDetailScreen
import org.nulllauncher.mobile.ui.screens.InstancesScreen
import org.nulllauncher.mobile.ui.screens.LogsScreen
import org.nulllauncher.mobile.ui.screens.ProfileScreen
import org.nulllauncher.mobile.ui.screens.SubscriptionScreen
import org.nulllauncher.mobile.ui.screens.VersionsScreen
import org.nulllauncher.mobile.data.instances.Instance
import org.nulllauncher.mobile.ui.theme.Acc
import org.nulllauncher.mobile.ui.theme.Acc2
import org.nulllauncher.mobile.ui.theme.NullTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NullTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1600)
        splash = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedVisibility(visible = !splash, enter = fadeIn(), exit = fadeOut()) {
            MainNav()
        }
        AnimatedVisibility(visible = splash, enter = fadeIn(), exit = fadeOut()) {
            SplashScreen()
        }
    }
}

/** Экран загрузки: логотип nullLauncher на фиолетово-синем градиенте */
@Composable
private fun SplashScreen() {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200),
        label = "splash",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .shadow(24.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Acc, Acc2)))
                    .alpha(alpha),
                contentAlignment = Alignment.Center,
            ) {
                Text("N", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            Text(
                "nullLauncher",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = alpha.let { Modifier.alpha(it) },
            )
            Text(
                "Mobile · 0.1.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/** Нижняя навигация из 4 вкладок */
@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val app = org.nulllauncher.mobile.NullApp.instance

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(app, nav) }
                composable("instances") { InstancesScreen(app, nav) }
                composable("versions") { VersionsScreen(app, nav) }
                composable("catalog") { CatalogScreen(app, nav) }
                composable("profile") { ProfileScreen(app, nav) }
                composable("subscription") { SubscriptionScreen(app, nav, app.billing) }
                composable("ai") { AIScreen(app, nav) }
                composable("create_instance") {
                    InstanceCreationWizard(app, nav) { inst: Instance ->
                        nav.popBackStack()
                        nav.navigate("instance/${inst.id}")
                    }
                }
                composable("logs") { LogsScreen(app, nav) }
                composable(
                    "instance/{instanceId}",
                    arguments = listOf(navArgument("instanceId") { type = NavType.StringType }),
                ) { back ->
                    InstanceDetailScreen(app, nav, back.arguments?.getString("instanceId") ?: return@composable)
                }
            }
        }
        // реактивный маршрут: без этого подсветка вкладки не обновляется
        val route by nav.currentBackStackEntryAsState()
        val currentRoute = route?.destination?.route ?: "home"
        // На детальных экранах нижняя панель скрыта — они закрываются кнопкой «назад»
        if (currentRoute in setOf("home", "instances", "versions", "catalog", "profile")) {
            BottomBar(
                current = currentRoute,
                onSelect = { r ->
                    nav.navigate(r) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
