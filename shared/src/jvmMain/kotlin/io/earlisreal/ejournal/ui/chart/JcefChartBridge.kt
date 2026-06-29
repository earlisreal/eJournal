package io.earlisreal.ejournal.ui.chart

import io.earlisreal.ejournal.ui.viewmodel.AnalysisState
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component
import java.io.File

class JcefChartBridge private constructor(private val pageUrl: String) {
    constructor() : this(defaultHtmlFileUrl())

    private val client = JcefRuntime.client()
    private val browser: CefBrowser = client.createBrowser(pageUrl, false, false)
    private val pending = ArrayDeque<String>()
    @Volatile private var loaded = false

    init {
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, code: Int) {
                if (b !== browser || frame?.isMain != true) return
                synchronized(pending) { loaded = true; pending.forEach(::exec0); pending.clear() }
            }
        })
    }

    val uiComponent: Component get() = browser.uiComponent

    fun sendState(state: AnalysisState, scrollToTrade: Boolean = true) {
        val chart = state.chartData ?: return
        val pos = state.position ?: return
        val tf = state.activeTimeframe
        val vwap = if (state.vwapEnabled && chart.vwap.isNotEmpty()) ChartSerialization.vwapJson(chart.vwap, tf) else "null"
        exec("setTheme(${state.isDarkTheme})")
        exec("setData(${ChartSerialization.candlesJson(chart.bars, tf)}, ${ChartSerialization.volumeJson(chart.bars, tf)}, $vwap)")
        exec("setMarkers(${ChartSerialization.markersJson(pos.transactions, tf)})")
        if (scrollToTrade) exec(ChartSerialization.initialViewCommand(pos.entryDatetime, pos.exitDatetime, chart.bars, tf))
        exec("updateTitle('${pos.symbol}', '${tf.label}')")
    }

    fun resize(w: Int, h: Int) = exec("resize($w, $h)")
    fun sendTheme(isDark: Boolean) = exec("setTheme($isDark)")
    fun dispose() = runCatching { browser.close(true) }.let {}

    private fun exec(js: String) = synchronized(pending) { if (loaded) exec0(js) else pending.add(js) }
    private fun exec0(js: String) = browser.executeJavaScript(js, browser.url ?: pageUrl, 0)

    companion object {
        fun forUrl(url: String) = JcefChartBridge(url)

        private fun defaultHtmlFileUrl(): String {
            val dir = File(System.getProperty("java.io.tmpdir"), "ejournal-chart-v5").also { it.mkdirs() }
            listOf("trade-analysis-v5.html", "trade-analysis-v5.js", "lightweight-charts-v5.standalone.production.js").forEach { name ->
                JcefChartBridge::class.java.getResourceAsStream("/chart/$name")
                    ?.use { src -> File(dir, name).outputStream().use { dst -> src.copyTo(dst) } }
            }
            return File(dir, "trade-analysis-v5.html").toURI().toString()
        }
    }
}
