package io.earlisreal.ejournal.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.earlisreal.ejournal.ui.viewmodel.AnalysisState

@Composable
actual fun CandlestickChart(state: AnalysisState, modifier: Modifier) {
    val bridge = remember { ChartBridge() }

    DisposableEffect(Unit) { onDispose { bridge.dispose() } }

    LaunchedEffect(state.chartData, state.vwapEnabled, state.position, state.isDarkTheme) {
        if (state.chartData != null && state.position != null) {
            bridge.sendState(state)
        } else {
            bridge.sendTheme(state.isDarkTheme)
        }
    }

    SwingPanel(
        modifier = modifier,
        factory = { bridge.uiComponent },
    )
}
