package org.nulllauncher.mobile.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import org.json.JSONObject
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.launch.LaunchState
import org.nulllauncher.mobile.data.runtime.JreInstaller

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Главный интерфейс лаунчера на HTML/CSS/JS (assets/ui/index.html),
 * как в ПК-версии. Kotlin-слой подключён через JavascriptInterface ([NullBridge]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(app: NullApp, nav: NavController) {
    var wv by remember { mutableStateOf<WebView?>(null) }

    fun push(type: String, obj: JSONObject) {
        val js = "window.__push&&__push(" + JSONObject.quote(type) + "," + obj + ")"
        wv?.evaluateJavascript(js, null)
    }

    val launchState by app.launcher.state.collectAsState()
    val install by app.installProgress.collectAsState()
    val lastError by app.lastError.collectAsState()
    val javaMajor by JreInstaller.busyMajor.collectAsState()
    val javaStage by JreInstaller.busyStage.collectAsState()
    val javaProgress by JreInstaller.busyProgress.collectAsState()

    // ---- события Kotlin → JS ----
    LaunchedEffect(launchState) {
        val o = JSONObject()
        when (val s = launchState) {
            is LaunchState.Preparing -> { o.put("state", "preparing").put("stage", s.stage) }
            is LaunchState.Running -> { o.put("state", "running").put("stage", "") }
            is LaunchState.Failed -> {
                o.put("state", "failed").put("stage", s.title).put("details", s.details)
            }
            LaunchState.Stopped -> o.put("state", "stopped")
            else -> o.put("state", "idle")
        }
        push("launch", o)
    }
    LaunchedEffect(install) {
        val p = install
        push(
            "install",
            if (p == null) JSONObject().put("on", false)
            else JSONObject().put("on", true).put("stage", p.stage).put("done", p.done).put("total", p.total),
        )
    }
    LaunchedEffect(lastError) {
        lastError?.let { push("error", JSONObject().put("message", it)) }
    }
    LaunchedEffect(javaMajor, javaStage, javaProgress) {
        push(
            "java",
            JSONObject()
                .put("major", javaMajor ?: -1)
                .put("stage", javaStage ?: "")
                .put("progress", javaProgress ?: -1f),
        )
    }

    // ---- системная кнопка «назад» ----
    BackHandler {
        val view = wv
        val activity = view?.context?.findActivity()
        if (view != null) {
            view.evaluateJavascript("(window.__back?window.__back():false)?'1':'0'") { r ->
                if (r != "'1'") activity?.finish()
            }
        } else {
            activity?.finish()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.textZoom = 100
                setBackgroundColor(0xFFF4F7F5.toInt())
                webViewClient = WebViewClient()
                val bridge = NullBridge(app)
                bridge.onClassic = { nav.navigate("home") }
                addJavascriptInterface(bridge, "Null")
                wv = this
                loadUrl("file:///android_asset/ui/index.html")
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier,
    )
}
