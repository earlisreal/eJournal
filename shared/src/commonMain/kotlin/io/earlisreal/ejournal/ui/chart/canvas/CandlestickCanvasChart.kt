package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Native Compose-[Canvas] candlestick chart — the spike alternative to the JCEF/Lightweight-Charts
 * bridge. Interaction: drag to pan, scroll to zoom (cursor-anchored), hover for the crosshair + OHLCV
 * legend. All drawing goes through [drawCandlestickChart]; state here is just the visible [BarWindow]
 * and the hovered bar. Interaction uses the common `awaitPointerEventScope` API (not the desktop-only
 * `onPointerEvent`) so the composable stays in commonMain.
 */
@Composable
fun CandlestickCanvasChart(
    bars: List<Bar>,
    markers: List<PriceMarker>,
    title: String,
    modifier: Modifier = Modifier,
    vwap: List<LinePoint> = emptyList(),
    intraday: Boolean = false,
    initialWindow: BarWindow = BarWindow.initial(bars.size),
) {
    val appColors = AppTheme.colors
    val colors = remember(appColors) { ChartColors.from(appColors) }
    val textMeasurer = rememberTextMeasurer()
    var window by remember(bars) { mutableStateOf(initialWindow) }
    var crosshair by remember(bars) { mutableStateOf<Int?>(null) }

    fun plotWidthOf(totalWidthPx: Int, rightAxisPx: Float) = (totalWidthPx - rightAxisPx).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .pointerInput(bars) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val plotWidth = plotWidthOf(size.width, RIGHT_AXIS_DP.toPx())
                    val slot = plotWidth / window.visibleBars.coerceAtLeast(1)
                    val deltaBars = (-drag.x / slot).roundToInt()
                    if (deltaBars != 0) window = window.pan(deltaBars, bars.size)
                }
            }
            .pointerInput(bars) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val plotWidth = plotWidthOf(size.width, RIGHT_AXIS_DP.toPx())
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val change = event.changes.first()
                                val focus = (change.position.x / plotWidth).coerceIn(0f, 1f).toDouble()
                                val factor = if (change.scrollDelta.y > 0f) 1.15 else 0.87 // scroll down = zoom out
                                window = window.zoom(factor, focus, bars.size)
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                val pos = event.changes.first().position
                                crosshair = if (bars.isNotEmpty() && pos.x in 0f..plotWidth) {
                                    ChartViewport.fit(bars, window).barIndexAt(pos.x, 0f, plotWidth)
                                } else null
                            }
                            PointerEventType.Exit -> crosshair = null
                        }
                    }
                }
            },
    ) {
        val viewport = ChartViewport.fit(bars, window)
        drawCandlestickChart(bars, markers, viewport, colors, textMeasurer, title, crosshair, vwap, intraday)
    }
}
