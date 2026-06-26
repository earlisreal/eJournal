package io.earlisreal.ejournal.ui.platform

import javafx.application.Platform

/**
 * Owns the JavaFX toolkit lifecycle for the whole app. Two features need the FX runtime:
 * the chart WebView ([io.earlisreal.ejournal.ui.chart.JavaFxChartBridge]) and the native file
 * picker ([pickImportFiles]). Either may be the first to start it, so the start is centralised
 * here and made idempotent.
 *
 * setImplicitExit(false) keeps the toolkit alive after the last FX surface is disposed (e.g.
 * navigating away from the Analysis chart) — see JavaFxChartBridge for why that matters. The
 * flip side is that the non-daemon FX thread would block JVM exit, so [shutdown] must run on app
 * close (wired from main.kt via JavaFxChartBridge.shutdown()).
 */
object JavaFxToolkit {
    @Volatile
    private var started = false

    /** Idempotent; safe from any thread. Returns once the toolkit is running. */
    @Synchronized
    fun ensureStarted() {
        if (started) return
        // Platform.startup throws IllegalStateException if the toolkit is already running (e.g. a
        // JFXPanel started it first). Treat that as success — we only care that it's up.
        runCatching { Platform.startup {} }
        Platform.setImplicitExit(false)
        started = true
    }

    /** Tear down the toolkit on app shutdown. No-op if it was never started. */
    fun shutdown() {
        if (started) runCatching { Platform.exit() }
    }
}
