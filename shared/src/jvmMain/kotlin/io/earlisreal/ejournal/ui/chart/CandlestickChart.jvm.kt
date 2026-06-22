package io.earlisreal.ejournal.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.earlisreal.ejournal.domain.marketdata.AggregatedChart
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.viewmodel.AnalysisState
import kotlin.math.roundToInt

@Composable
actual fun CandlestickChart(state: AnalysisState, modifier: Modifier) {
    val bridge = remember { ChartPreload.take() ?: JavaFxChartBridge() }
    val density = LocalDensity.current.density
    var prevPosition by remember { mutableStateOf<ClosedPosition?>(null) }
    var prevChartData by remember { mutableStateOf<AggregatedChart?>(null) }

    DisposableEffect(Unit) { onDispose { bridge.dispose() } }

    LaunchedEffect(state.chartData, state.vwapEnabled, state.position, state.isDarkTheme) {
        if (state.chartData != null && state.position != null) {
            // Re-anchor the view to the trade whenever the data reloads. A timeframe switch loads a
            // new bar set, so the scroll must be re-applied or the chart lands on the first/last bar
            // instead of the trade. A new chartData instance means a reload (position OR timeframe
            // change); a VWAP/theme toggle keeps the same reference, so the view is left untouched.
            val scrollToTrade = state.position != prevPosition || state.chartData !== prevChartData
            prevPosition = state.position
            prevChartData = state.chartData
            bridge.sendState(state, scrollToTrade)
        } else {
            bridge.sendTheme(state.isDarkTheme)
        }
    }

    SwingPanel(
        // onSizeChanged fires during Compose layout (before effects), guaranteeing the
        // chart is resized to the real dimensions before setData runs.
        modifier = modifier.onSizeChanged { size ->
            val w = (size.width / density).roundToInt()
            val h = (size.height / density).roundToInt()
            if (w > 0 && h > 0) bridge.resize(w, h)
        },
        factory = { bridge.uiComponent },
    )
}
