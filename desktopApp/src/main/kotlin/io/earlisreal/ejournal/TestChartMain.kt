package io.earlisreal.ejournal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.ui.chart.JavaFxChartBridge
import java.io.File
import kotlin.math.roundToInt

/**
 * Chart integration diagnostics.
 *
 * Run via:
 *   ./gradlew :desktopApp:run --args="chart-test"
 *
 * Renders Lightweight Charts v4 in a JavaFX WebView (via SwingPanel). v4 is compatible with
 * JavaFX's bundled WebKit, so no external Chromium/JCEF/KCEF runtime is needed — it works on a
 * stock JDK with only the JavaFX dependencies. The page (test-chart.html) self-renders sample
 * candles/volume/VWAP/markers on load.
 */
fun runChartTest(mode: String) {
    println("[chart-test] mode: $mode (Lightweight Charts v4 · JavaFX WebView)")
    runJavaFxTest(extractTestChartFiles())
}

// ── File extraction ──────────────────────────────────────────────────────────

private fun extractTestChartFiles(): String {
    val dir = File(System.getProperty("java.io.tmpdir"), "ejournal-chart-test").also { it.mkdirs() }
    listOf(
        "test-chart.html",
        "trade-analysis.js",
        "lightweight-charts.standalone.production.js",
    ).forEach { name ->
        val stream = object {}::class.java.getResourceAsStream("/chart/$name")
        if (stream != null) {
            stream.use { src -> File(dir, name).outputStream().use { dst -> src.copyTo(dst) } }
            println("[chart-test] extracted /chart/$name")
        } else {
            System.err.println("[chart-test] WARNING: /chart/$name NOT found on classpath")
        }
    }
    return File(dir, "test-chart.html").toURI().toString()
}

// ── JavaFX WebView via SwingPanel ─────────────────────────────────────────────

private fun runJavaFxTest(url: String) {
    println("[chart-test] creating JavaFxChartBridge for $url")
    val bridge = JavaFxChartBridge.forUrl(url)

    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 750.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Chart Test — JavaFX WebView (Lightweight Charts v4)",
        ) {
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
