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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
        toolkitStarted = true
        // Keep the JavaFX toolkit alive across chart-panel disposal (navigating away from the
        // Analysis screen). With the default implicit-exit, removing the last JFXPanel terminates
        // the QuantumRenderer; async WebKit image cleanup (RTImage.dispose) then submits to the dead
        // renderer → RejectedExecutionException. The toolkit is torn down explicitly in shutdown().
        Platform.setImplicitExit(false)
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
                    try {
                        val r = engine.executeScript(
                            "(function(){" +
                                "if(typeof chart==='undefined')return 'chart not ready';" +
                                "var g=chart.timeScale().getVisibleLogicalRange();" +
                                "return g?(g.from.toFixed(1)+'..'+g.to.toFixed(1)):'null';" +
                            "})()"
                        )
                        println("[chart] VERIFY visibleRange $r")
                    } catch (e: Exception) {
                        System.err.println("[chart] probe failed: $e")
                    }
                }
            }
            engine.load(pageUrl)
        }
    }

    // All JavaFX types are kept private; callers only need java.awt.Component.
    val uiComponent: Component get() = jfxPanel

    fun sendState(state: AnalysisState, scrollToTrade: Boolean = true) {
        val chartData = state.chartData ?: return
        val position = state.position ?: return
        val tf = state.activeTimeframe

        val candlesJson = serializeCandles(chartData.bars, tf)
        val volumeJson  = serializeVolume(chartData.bars, tf)
        val vwapJson    = if (state.vwapEnabled && chartData.vwap.isNotEmpty())
            serializeVwap(chartData.vwap, tf) else "null"
        val markersJson = serializeMarkers(position.transactions, tf)

        exec("setTheme(${state.isDarkTheme})")
        exec("setData($candlesJson, $volumeJson, $vwapJson)")
        exec("setMarkers($markersJson)")
        if (scrollToTrade) {
            exec(initialViewCommand(position.entryDatetime, position.exitDatetime, chartData.bars, tf))
        }
        exec("updateTitle('${position.symbol}', '${tf.label}')")
    }

    // Daily/weekly charts load the symbol's full history, so frame the trade with an explicit
    // visible range [entry-90d, exit+60d] (clamped to the loaded data) and let the user zoom out
    // from there. Intraday data is already a tight per-trade window, so just scroll the entry into
    // view at the current zoom.
    private fun initialViewCommand(
        entry: LocalDateTime,
        exit: LocalDateTime,
        bars: List<Bar>,
        tf: ChartTimeframe,
    ): String = when (tf) {
        ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> {
            val firstSec = bars.firstOrNull()?.let { barTimeSec(it.timestamp, tf) }
            val lastSec = bars.lastOrNull()?.let { barTimeSec(it.timestamp, tf) }
            if (firstSec == null || lastSec == null) {
                "scrollToFirstTrade(0)"
            } else {
                val from = maxOf(dateEpochSec(entry.date.minus(DatePeriod(days = VIEW_LEAD_DAYS))), firstSec)
                val to = minOf(dateEpochSec(exit.date.plus(DatePeriod(days = VIEW_TAIL_DAYS))), lastSec)
                if (from >= to) "scrollToFirstTrade(${firstTradeBarIndex(entry, bars, tf)})"
                else "setVisibleRange($from, $to)"
            }
        }
        else -> "scrollToFirstTrade(${firstTradeBarIndex(entry, bars, tf)})"
    }

    private fun dateEpochSec(date: kotlinx.datetime.LocalDate): Long =
        date.atStartOfDayIn(TimeZone.UTC).epochSeconds

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

    // Always emit numeric UTC epoch seconds so Lightweight Charts v4 never has to
    // switch between UTCTimestamp and BusinessDay time types (which throws at runtime).
    // For daily/weekly bars, normalise to midnight UTC of that date so day-granularity
    // comparisons work correctly regardless of whether the raw bar is at market-open.
    private fun barTimeSec(ts: LocalDateTime, tf: ChartTimeframe): Long = when (tf) {
        ChartTimeframe.DAILY,
        ChartTimeframe.WEEKLY -> ts.date.atStartOfDayIn(TimeZone.UTC).epochSeconds
        else                  -> ts.toInstant(TimeZone.UTC).epochSeconds
    }

    private fun barTime(ts: LocalDateTime, tf: ChartTimeframe): String = barTimeSec(ts, tf).toString()

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

    // Snap a transaction time down to the start of its timeframe bucket so it lines up with a bar.
    private fun snapToBucket(dt: LocalDateTime, tf: ChartTimeframe): LocalDateTime = when (tf) {
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

    private fun txTime(dt: LocalDateTime, tf: ChartTimeframe): String =
        barTime(snapToBucket(dt, tf), tf)

    private fun serializeMarkers(transactions: List<Transaction>, tf: ChartTimeframe): String {
        val seen = mutableSetOf<Long>()
        val unique = transactions.filter { seen.add(it.id) }
        return unique.joinToString(",", "[", "]") { tx ->
            val color = if (tx.action == Action.BUY) "rgba(165,214,167,0.8)" else "rgba(244,143,177,0.8)"
            // Diamond at the exact fill price: numeric position = price (custom v4 fork build —
            // see ~/Projects/lwc-v4-diamond). Lighter/transparent colors so markers stand out
            // against the candle bars.
            """{"time":${txTime(tx.datetime, tf)},"position":${tx.price},"shape":"diamond","color":"$color","size":1.65}"""
        }
    }

    // Bar index of the first trade (entry), snapped to the active timeframe so it lines up with the
    // entry marker. The JS side scrolls so this bar sits a few bars in from the left edge.
    private fun firstTradeBarIndex(entryDatetime: LocalDateTime, bars: List<Bar>, tf: ChartTimeframe): Int {
        val idx = when (tf) {
            ChartTimeframe.DAILY, ChartTimeframe.WEEKLY ->
                bars.indexOfFirst { it.timestamp.date >= entryDatetime.date }
            else -> {
                val snapped = snapToBucket(entryDatetime, tf)
                bars.indexOfFirst { it.timestamp >= snapped }
            }
        }
        return if (idx < 0) 0 else idx
    }

    companion object {
        // Initial daily/weekly view window around the trade: ~90 days of pre-entry setup and ~60
        // days of aftermath. Just the opening zoom — the full history is loaded and scrollable.
        private const val VIEW_LEAD_DAYS = 90
        private const val VIEW_TAIL_DAYS = 60

        @Volatile private var toolkitStarted = false

        init {
            // Our JavaFX (org.openjfx) jars load from the classpath (the unnamed module), so JavaFX
            // logs a benign one-shot JUL WARNING when the toolkit starts: "Unsupported JavaFX
            // configuration: classes were loaded from 'unnamed module ...'". It's emitted via
            // com.sun.javafx.util.Logging.getJavaFXLogger(), whose JUL logger is named "javafx" — NOT
            // the PlatformImpl source class shown in the log line. Modularising a Compose Desktop app
            // to silence it isn't worth the jpackage risk, so we raise that logger above WARNING. This
            // runs at class-load, before the first JFXPanel() starts the toolkit, so the level is in
            // effect by the time the warning would fire (verified). Real SEVERE FX errors still
            // surface; switch to Level.WARNING here when diagnosing an actual FX startup failure.
            java.util.logging.Logger.getLogger("javafx")
                .level = java.util.logging.Level.SEVERE
        }

        /**
         * Tear down the JavaFX toolkit on app shutdown. Required because setImplicitExit(false)
         * keeps the non-daemon FX thread alive, which would otherwise block JVM exit. No-op if the
         * chart was never opened (toolkit never started).
         */
        fun shutdown() {
            if (toolkitStarted) runCatching { Platform.exit() }
        }

        fun forUrl(url: String) = JavaFxChartBridge(url)

        private fun defaultHtmlFileUrl(): String {
            val dir = File(System.getProperty("java.io.tmpdir"), "ejournal-chart").also { it.mkdirs() }
            // trade-analysis.html loads trade-analysis.js via <script src> — it must be copied
            // alongside, or resize()/setData()/etc. are undefined in the WebView.
            listOf(
                "trade-analysis.html",
                "trade-analysis.js",
                "lightweight-charts.standalone.production.js",
            ).forEach { name ->
                JavaFxChartBridge::class.java.getResourceAsStream("/chart/$name")
                    ?.use { src -> File(dir, name).outputStream().use { dst -> src.copyTo(dst) } }
            }
            return File(dir, "trade-analysis.html").toURI().toString()
        }
    }
}
