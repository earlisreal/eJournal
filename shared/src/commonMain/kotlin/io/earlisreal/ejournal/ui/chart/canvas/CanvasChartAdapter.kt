package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.VwapPoint
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.viewmodel.AnalysisState

/**
 * Maps [AnalysisState] onto the plain data the native [CandlestickCanvasChart] draws. This is the
 * production entry point for the Analysis screen's chart.
 */
@Composable
fun CandlestickCanvasChart(state: AnalysisState, modifier: Modifier = Modifier) {
    val bars = state.chartData?.bars ?: emptyList()
    val position = state.position
    val tf = state.activeTimeframe
    val intraday = tf in INTRADAY

    // Precompute the derived overlays once per data load, not per frame.
    val markers = remember(bars, position) { if (position != null) markersFor(position, bars) else emptyList() }
    val vwapLine = remember(bars, state.vwapEnabled, state.chartData) {
        if (state.vwapEnabled) vwapFor(bars, state.chartData?.vwap ?: emptyList()) else emptyList()
    }
    val initialWindow = remember(bars, position, tf) {
        if (position != null && bars.isNotEmpty())
            ChartInitialView.forTrade(bars, position.entryDatetime, position.exitDatetime, tf)
        else BarWindow.initial(bars.size)
    }

    val title = "${position?.symbol ?: ""} · ${tf.label}"
    CandlestickCanvasChart(
        bars = bars,
        markers = markers,
        title = title,
        modifier = modifier,
        vwap = vwapLine,
        intraday = intraday,
        initialWindow = initialWindow,
    )
}

private val INTRADAY = setOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN)

/**
 * Place each trade fill on the bar that contains it — the last bar whose timestamp is at or before
 * the fill time (works for daily bars at midnight and intraday bars at the minute). Dedupes by
 * transaction id, matching the JCEF chart's marker handling.
 */
private fun markersFor(position: ClosedPosition, bars: List<Bar>): List<PriceMarker> {
    if (bars.isEmpty()) return emptyList()
    val seen = HashSet<Long>()
    return position.transactions.filter { seen.add(it.id) }.mapNotNull { tx ->
        val idx = bars.indexOfLast { it.timestamp <= tx.datetime }
        if (idx < 0) null else PriceMarker(barIndex = idx, price = tx.price, isBuy = tx.action == Action.BUY)
    }
}

/** Anchor each VWAP point to its bar by timestamp so the line tracks the candles. */
private fun vwapFor(bars: List<Bar>, vwap: List<VwapPoint>): List<LinePoint> {
    if (bars.isEmpty() || vwap.isEmpty()) return emptyList()
    return vwap.mapNotNull { p ->
        val idx = bars.indexOfLast { it.timestamp <= p.timestamp }
        if (idx < 0) null else LinePoint(barIndex = idx, value = p.value)
    }
}
