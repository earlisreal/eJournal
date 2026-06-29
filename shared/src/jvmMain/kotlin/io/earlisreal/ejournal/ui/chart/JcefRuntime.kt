package io.earlisreal.ejournal.ui.chart

import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.EnumProgress
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import java.io.File

/**
 * Owns the JCEF (jcefmaven) lifecycle for the spike: builds the singleton [CefApp] + shared
 * [CefClient]. [warmUp] blocks while jcefmaven downloads + installs the matching CEF native bundle
 * (~100MB) on first run; subsequent runs reuse the install dir and return immediately.
 *
 * Call [warmUp] on a plain thread BEFORE Compose's application{} starts the AWT/Skiko event loop —
 * the standard jcefmaven ordering (build CefApp, then show UI). Browsers are created via [client].
 */
object JcefRuntime {
    private const val TAG = "[jcef]"

    @Volatile private var cefApp: CefApp? = null
    @Volatile private var cefClient: CefClient? = null

    /** Persisted so the ~100MB CEF bundle is downloaded once, not per run. */
    private val installDir = File(System.getProperty("user.home"), ".ejournal/jcef-bundle")

    @Synchronized
    fun warmUp() {
        if (cefApp != null) return
        val t = System.currentTimeMillis()
        println("$TAG installDir=$installDir")
        val builder = CefAppBuilder()
        builder.setInstallDir(installDir)
        // Windowed (heavyweight) rendering — the browser owns a native surface inside the SwingPanel.
        builder.cefSettings.windowless_rendering_enabled = false
        builder.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
        builder.setProgressHandler { state: EnumProgress, percent: Float ->
            if (state == EnumProgress.DOWNLOADING && percent >= 0f) println("$TAG downloading CEF: ${percent.toInt()}%")
            else println("$TAG init: $state")
        }
        builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
            override fun stateHasChanged(state: CefApp.CefAppState) {
                println("$TAG CefAppState=$state")
            }
        })
        val app = builder.build() // blocks: install (first run) + native init
        cefApp = app
        cefClient = app.createClient()
        val version = runCatching { app.version }.getOrNull()
        println("$TAG ready in ${System.currentTimeMillis() - t}ms; version=$version")
    }

    fun client(): CefClient {
        warmUp()
        return cefClient ?: error("CefClient unavailable after warmUp()")
    }

    fun dispose() {
        runCatching { cefClient?.dispose() }
        runCatching { cefApp?.dispose() }
        cefClient = null
        cefApp = null
    }
}
