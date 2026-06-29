package io.earlisreal.ejournal.ui.platform

import javafx.application.Platform

/**
 * Owns the JavaFX toolkit lifecycle for the whole app. JavaFX is now used solely for the native
 * file picker ([pickImportFiles]); the chart moved to JCEF ([io.earlisreal.ejournal.ui.chart.JcefChartBridge]).
 *
 * setImplicitExit(false) keeps the toolkit alive between picker invocations so the non-daemon FX
 * thread does not race on re-init. The flip side is that the FX thread would block JVM exit, so
 * [shutdown] must run on app close — wired from main.kt via [JavaFxToolkit.shutdown].
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
