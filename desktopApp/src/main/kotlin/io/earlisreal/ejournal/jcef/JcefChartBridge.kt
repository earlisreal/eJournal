package io.earlisreal.ejournal.jcef

import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component

/**
 * JCEF analogue of [io.earlisreal.ejournal.ui.chart.JavaFxChartBridge] for the spike: wraps a single
 * windowed [CefBrowser] loading the v5 chart page, exposes its AWT [uiComponent] for a Compose
 * SwingPanel, and pushes data one-way via executeJavaScript (mirroring the JavaFX bridge's exec()).
 *
 * The [client] comes from [JcefRuntime] (jcefmaven); exposes the standard org.cef API.
 *
 * JS sent before the page finishes loading is queued and flushed on main-frame onLoadEnd.
 */
class JcefChartBridge(private val client: CefClient, private val pageUrl: String) {
    private val browser: CefBrowser = client.createBrowser(pageUrl, /* OSR = */ false, /* transparent = */ false)
    private val pending = ArrayDeque<String>()
    @Volatile private var loaded = false

    init {
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (b !== browser || frame?.isMain != true) return
                synchronized(pending) {
                    loaded = true
                    println("[jcef] page loaded (status=$httpStatusCode), flushing ${pending.size} queued calls")
                    pending.forEach(::exec0)
                    pending.clear()
                }
            }
        })
    }

    /** Heavyweight AWT component owned by CEF — drops straight into Compose's SwingPanel factory. */
    val uiComponent: Component get() = browser.uiComponent

    fun exec(js: String) = synchronized(pending) { if (loaded) exec0(js) else pending.add(js) }

    private fun exec0(js: String) = browser.executeJavaScript(js, browser.url ?: pageUrl, 0)

    fun resize(w: Int, h: Int) = exec("resize($w, $h)")
    fun setTheme(dark: Boolean) = exec("setTheme($dark)")

    fun dispose() = runCatching { browser.close(true) }.let {}
}
