package io.earlisreal.ejournal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.jcef.JbrJcefRuntime
import io.earlisreal.ejournal.jcef.JcefChartBridge
import io.earlisreal.ejournal.jcef.JcefRuntime
import org.cef.CefClient
import java.io.File
import kotlin.math.roundToInt

/**
 * JCEF + Lightweight Charts v5 spike harness, with two interchangeable engines:
 *   ./gradlew :desktopApp:runJcefTest      -> jcefmaven (downloads its own CEF)         [jcef-test]
 *   ./gradlew :desktopApp:runJcefJbrTest   -> JBR's bundled jcef module (matched CEF)   [jcef-jbr-test]
 *
 * Both render the same v5 page (jcef-test-v5.html) — self-rendered sample candles/volume + exact-price
 * diamond markers (a stock-v5 series primitive) — in a real-Chromium browser inside a Compose SwingPanel.
 */
fun runJcefChartTest() = runChart(
    title = "JCEF spike — Lightweight Charts v5 (jcefmaven)",
    client = { JcefRuntime.warmUp(); JcefRuntime.client() },
    dispose = { JcefRuntime.dispose() },
)

fun runJbrJcefChartTest() = runChart(
    title = "JCEF spike — Lightweight Charts v5 (JBR jcef module)",
    client = { JbrJcefRuntime.warmUp(); JbrJcefRuntime.client() },
    dispose = { JbrJcefRuntime.dispose() },
)

private fun runChart(title: String, client: () -> CefClient, dispose: () -> Unit) {
    println("[jcef-test] $title")
    val url = extractV5Files()
    // Build/warm CEF (and download on first run for jcefmaven) on the main thread BEFORE Compose
    // starts its event loop — the standard JCEF ordering.
    val cefClient = client()

    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 750.dp))
        Window(
            onCloseRequest = { dispose(); exitApplication() },
            state = windowState,
            title = title,
        ) {
            val bridge = remember { JcefChartBridge(cefClient, url) }
            val density = LocalDensity.current.density
            SwingPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        val w = (size.width / density).roundToInt()
                        val h = (size.height / density).roundToInt()
                        if (w > 0 && h > 0) bridge.resize(w, h)
                    },
                factory = { bridge.uiComponent },
            )
        }
    }
}

private fun extractV5Files(): String {
    val dir = File(System.getProperty("java.io.tmpdir"), "ejournal-jcef-v5").also { it.mkdirs() }
    listOf(
        "jcef-test-v5.html",
        "jcef-chart-v5.js",
        "lightweight-charts-v5.standalone.production.js",
    ).forEach { name ->
        val stream = object {}::class.java.getResourceAsStream("/chart/$name")
        if (stream != null) {
            stream.use { src -> File(dir, name).outputStream().use { dst -> src.copyTo(dst) } }
            println("[jcef-test] extracted /chart/$name")
        } else {
            System.err.println("[jcef-test] WARNING: /chart/$name NOT found on classpath")
        }
    }
    return File(dir, "jcef-test-v5.html").toURI().toString()
}
