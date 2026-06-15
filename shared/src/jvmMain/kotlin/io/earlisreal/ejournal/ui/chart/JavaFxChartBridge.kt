package io.earlisreal.ejournal.ui.chart

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.VwapPoint
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.ui.viewmodel.AnalysisState
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.awt.Component
import java.io.File

class JavaFxChartBridge private constructor(private val pageUrl: String) {

    constructor() : this(defaultHtmlFileUrl())

    // JFXPanel creation initializes the JavaFX runtime; must happen before Platform.runLater.
    private val jfxPanel = JFXPanel()
    private var webEngine: WebEngine? = null
    private val pendingJs = mutableListOf<String>()
    private var loaded = false

    init {
        Platform.runLater {
            val webView = WebView()
            val engine = webView.engine
            webEngine = engine
            jfxPanel.scene = Scene(webView)
            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                if (newState == Worker.State.SUCCEEDED) {
                    loaded = true
                    pendingJs.forEach { js ->
                        try { engine.executeScript(js) }
                        catch (e: Exception) { System.err.println("[chart] flush error: $e") }
                    }
                    pendingJs.clear()
                }
            }
            engine.load(pageUrl)
        }
    }

    // All JavaFX types are kept private; callers only need java.awt.Component.
    val uiComponent: Component get() = jfxPanel

    fun sendState(state: AnalysisState) {
        val chartData = state.chartData ?: return
        val position = state.position ?: return
        val tf = state.activeTimeframe

        val candlesJson = serializeCandles(chartData.bars, tf)
        val volumeJson  = serializeVolume(chartData.bars, tf)
        val vwapJson    = if (state.vwapEnabled && chartData.vwap.isNotEmpty())
            serializeVwap(chartData.vwap, tf) else "null"
        val markersJson = serializeMarkers(position.transactions, tf)
        val scrollPos   = scrollPosition(position.entryDatetime, chartData.bars)

        exec("setTheme(${state.isDarkTheme})")
        exec("setData($candlesJson, $volumeJson, $vwapJson)")
        exec("setMarkers($markersJson)")
        exec("scrollTo($scrollPos)")
        exec("updateTitle('${position.symbol}', '${tf.label}')")
    }

    fun resize(w: Int, h: Int) = exec("resize($w, $h)")

    fun sendTheme(isDark: Boolean) = exec("setTheme($isDark)")

    fun dispose() {
        Platform.runLater { webEngine?.loadContent("") }
    }

    // All exec calls are dispatched onto the JavaFX Application Thread, so pendingJs
    // and loaded are only ever accessed from one thread — no synchronisation needed.
    private fun exec(js: String) {
        Platform.runLater {
            if (loaded) {
                try { webEngine!!.executeScript(js) }
                catch (e: Exception) { System.err.println("[chart] JS error: $e\n  script: ${js.take(120)}") }
            } else {
                pendingJs.add(js)
            }
        }
    }

    // ── Serialisation ────────────────────────────────────────────────────────────

    private fun barTime(ts: LocalDateTime, tf: ChartTimeframe): String = when (tf) {
        ChartTimeframe.ONE_MIN,
        ChartTimeframe.FIVE_MIN,
        ChartTimeframe.FIFTEEN_MIN -> ts.toInstant(TimeZone.UTC).epochSeconds.toString()
        ChartTimeframe.DAILY,
        ChartTimeframe.WEEKLY      -> "\"${ts.date}\""
    }

    private fun serializeCandles(bars: List<Bar>, tf: ChartTimeframe): String =
        bars.joinToString(",", "[", "]") { b ->
            """{"time":${barTime(b.timestamp, tf)},"open":${b.open},"high":${b.high},"low":${b.low},"close":${b.close}}"""
        }

    private fun serializeVolume(bars: List<Bar>, tf: ChartTimeframe): String =
        bars.joinToString(",", "[", "]") { b ->
            val color = if (b.close >= b.open) "rgba(38,166,154,0.5)" else "rgba(239,83,80,0.5)"
            """{"time":${barTime(b.timestamp, tf)},"value":${b.volume},"color":"$color"}"""
        }

    private fun serializeVwap(vwap: List<VwapPoint>, tf: ChartTimeframe): String =
        vwap.joinToString(",", "[", "]") { v ->
            """{"time":${barTime(v.timestamp, tf)},"value":${"%.4f".format(v.value)}}"""
        }

    private fun txTime(dt: LocalDateTime, tf: ChartTimeframe): String {
        val snapped = when (tf) {
            ChartTimeframe.ONE_MIN -> LocalDateTime(dt.date, LocalTime(dt.hour, dt.minute))
            ChartTimeframe.FIVE_MIN -> {
                val m = dt.minute - (dt.minute % 5)
                LocalDateTime(dt.date, LocalTime(dt.hour, m))
            }
            ChartTimeframe.FIFTEEN_MIN -> {
                val total = dt.hour * 60 + dt.minute
                val bucket = total - (total % 15)
                LocalDateTime(dt.date, LocalTime(bucket / 60, bucket % 60))
            }
            ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> dt
        }
        return barTime(snapped, tf)
    }

    private fun serializeMarkers(transactions: List<Transaction>, tf: ChartTimeframe): String {
        val seen = mutableSetOf<Long>()
        val unique = transactions.filter { seen.add(it.id) }
        return unique.joinToString(",", "[", "]") { tx ->
            val color = if (tx.action == Action.BUY) "rgba(165,214,167,0.9)" else "rgba(244,143,177,0.9)"
            """{"time":${txTime(tx.datetime, tf)},"position":"atPriceMiddle","price":${tx.price},"shape":"circle","color":"$color","size":1.5}"""
        }
    }

    private fun scrollPosition(entryDatetime: LocalDateTime, bars: List<Bar>): Int {
        val entryDate = entryDatetime.date
        val idx = bars.indexOfFirst { it.timestamp.date >= entryDate }
        return if (idx < 0) -40 else (idx - bars.size + 40)
    }

    companion object {
        fun forUrl(url: String) = JavaFxChartBridge(url)

        private fun defaultHtmlFileUrl(): String {
            val dir = File(System.getProperty("java.io.tmpdir"), "ejournal-chart").also { it.mkdirs() }
            listOf("trade-analysis.html", "trade-analysis.js", "lightweight-charts.standalone.production.js").forEach { name ->
                JavaFxChartBridge::class.java.getResourceAsStream("/chart/$name")
                    ?.use { src -> File(dir, name).outputStream().use { dst -> src.copyTo(dst) } }
            }
            return File(dir, "trade-analysis.html").toURI().toString()
        }
    }
}
