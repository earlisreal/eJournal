package io.earlisreal.ejournal.ui.chart.canvas

import io.earlisreal.ejournal.domain.marketdata.Bar
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Which bars are currently in view. Pure data + transforms — no Compose, no pixels — so pan/zoom
 * logic is unit-testable in isolation (mirrors how [io.earlisreal.ejournal.ui.chart.ChartSerialization]
 * is tested for the JCEF chart).
 */
data class BarWindow(val startIndex: Int, val visibleBars: Int) {

    /** Shift the window by whole bars, clamped so it never scrolls past either end of the data. */
    fun pan(deltaBars: Int, totalBars: Int): BarWindow =
        copy(startIndex = (startIndex + deltaBars).coerceIn(0, maxStart(totalBars, visibleBars)))

    /**
     * Change how many bars are visible while keeping the bar under [focusFraction] (0 = left edge,
     * 1 = right edge) pinned to that screen position. [factor] < 1 zooms in (fewer bars), > 1 zooms
     * out (more bars). Clamped to [[MIN_BARS], totalBars].
     */
    fun zoom(factor: Double, focusFraction: Double, totalBars: Int): BarWindow {
        if (totalBars <= 0) return this
        val lo = minOf(MIN_BARS, totalBars)
        val newVisible = (visibleBars * factor).roundToInt().coerceIn(lo, totalBars)
        val focusBarAbs = startIndex + focusFraction * visibleBars
        val newStart = (focusBarAbs - focusFraction * newVisible).roundToInt()
            .coerceIn(0, maxStart(totalBars, newVisible))
        return BarWindow(newStart, newVisible)
    }

    companion object {
        const val MIN_BARS = 5
        const val DEFAULT_VISIBLE = 120

        /** Open on the most recent [maxBars] (or the whole series if it's shorter). */
        fun initial(totalBars: Int, maxBars: Int = DEFAULT_VISIBLE): BarWindow {
            if (totalBars <= 0) return BarWindow(0, 0)
            val visible = minOf(totalBars, maxBars)
            return BarWindow(totalBars - visible, visible)
        }

        private fun maxStart(totalBars: Int, visibleBars: Int) = maxOf(0, totalBars - visibleBars)
    }
}

/**
 * A resolved view: the visible bar window plus the price/volume scales derived from those bars,
 * and the pure pixel-mapping functions the renderer uses. Given a plot rectangle it converts
 * bar-index ↔ x and price ↔ y.
 */
data class ChartViewport(
    val startIndex: Int,
    val visibleBars: Int,
    val priceLow: Double,
    val priceHigh: Double,
    val maxVolume: Double,
) {
    fun slotWidth(plotWidth: Float): Float = if (visibleBars <= 0) plotWidth else plotWidth / visibleBars

    fun xCenter(index: Int, plotLeft: Float, plotWidth: Float): Float =
        plotLeft + (index - startIndex + 0.5f) * slotWidth(plotWidth)

    fun priceToY(price: Double, plotTop: Float, plotHeight: Float): Float {
        val span = (priceHigh - priceLow).takeIf { it > 0.0 } ?: 1.0
        return plotTop + ((priceHigh - price) / span).toFloat() * plotHeight
    }

    fun volumeToHeight(volume: Long, bandHeight: Float): Float =
        if (maxVolume <= 0.0) 0f else (volume / maxVolume).toFloat() * bandHeight

    fun barIndexAt(x: Float, plotLeft: Float, plotWidth: Float): Int {
        val sw = slotWidth(plotWidth)
        if (visibleBars <= 0 || sw <= 0f) return startIndex
        val idx = startIndex + floor((x - plotLeft) / sw).toInt()
        return idx.coerceIn(startIndex, startIndex + visibleBars - 1)
    }

    companion object {
        fun fit(bars: List<Bar>, window: BarWindow, padFraction: Double = 0.06): ChartViewport {
            if (bars.isEmpty()) return ChartViewport(0, 0, 0.0, 1.0, 1.0)
            val start = window.startIndex.coerceIn(0, bars.lastIndex)
            val end = (start + window.visibleBars).coerceIn(start + 1, bars.size)
            val slice = bars.subList(start, end)
            val low = slice.minOf { it.low }
            val high = slice.maxOf { it.high }
            val span = high - low
            val pad = when {
                span > 0.0 -> span * padFraction
                high != 0.0 -> high * 0.01
                else -> 1.0
            }
            val maxVolume = slice.maxOf { it.volume }.toDouble().coerceAtLeast(1.0)
            return ChartViewport(start, end - start, low - pad, high + pad, maxVolume)
        }
    }
}
