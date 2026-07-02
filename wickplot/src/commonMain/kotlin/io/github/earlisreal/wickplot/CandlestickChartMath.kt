package io.github.earlisreal.wickplot

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Which bars are currently in view. Pure data + transforms — no Compose, no pixels — so pan/zoom
 * logic is unit-testable in isolation.
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

    /** Inverse of [priceToY]: the price at a pixel [y], so the free crosshair can label the cursor. */
    fun yToPrice(y: Float, plotTop: Float, plotHeight: Float): Double {
        if (plotHeight <= 0f) return priceHigh
        val span = (priceHigh - priceLow).takeIf { it > 0.0 } ?: 1.0
        return priceHigh - (y - plotTop).toDouble() / plotHeight * span
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
        fun fit(bars: List<Candle>, window: BarWindow, padFraction: Double = 0.06): ChartViewport {
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

/** A set of round price-grid levels plus the [step] between them (so labels can size their decimals). */
data class PriceGrid(val step: Double, val values: List<Double>)

/**
 * Round [raw] up to a "nice" axis step — 1, 2, 2.5, or 5 times a power of ten. This is the classic
 * nice-number rule financial charts (TradingView, Lightweight-Charts) use so gridlines land on
 * human-friendly values like 0.05, 0.10, 0.50, 1, 2, 5, 10 rather than arbitrary fractions.
 */
fun niceAxisStep(raw: Double): Double {
    if (raw <= 0.0 || raw.isNaN() || raw.isInfinite()) return 1.0
    val base = 10.0.pow(floor(log10(raw)))
    val mantissa = raw / base // in [1, 10)
    val eps = 1e-9 // so a mantissa sitting on a boundary (e.g. 2.0000000000000018) snaps to the lower nice value
    val nice = when {
        mantissa <= 1.0 + eps -> 1.0
        mantissa <= 2.0 + eps -> 2.0
        mantissa <= 2.5 + eps -> 2.5
        mantissa <= 5.0 + eps -> 5.0
        else -> 10.0
    }
    return nice * base
}

/** Fractional digits needed to print [step] exactly: 0.05 → 2, 0.5 → 1, 2.5 → 1, 1 → 0, 10 → 0. */
fun axisDecimals(step: Double): Int {
    var d = 0
    var s = step
    while (d < 8 && abs(s - round(s)) > 1e-9) { s *= 10; d++ }
    return d
}

/**
 * Round price-grid levels spanning [low]..[high] at a nice [step][niceAxisStep], aiming for about
 * [targetCount] lines. Levels are whole multiples of the step, so they read as round numbers strictly
 * inside the range (the padded high/low themselves are not labelled) — TradingView's price-axis look.
 */
fun priceGrid(low: Double, high: Double, targetCount: Int): PriceGrid {
    val range = high - low
    if (range <= 0.0 || targetCount <= 0) return PriceGrid(1.0, listOf(low))
    val step = niceAxisStep(range / targetCount)
    val eps = step * 1e-6 // absorb FP drift so a level sitting exactly on low/high isn't dropped
    val firstK = ceil((low - eps) / step).toLong()
    val lastK = floor((high + eps) / step).toLong()
    val values = if (lastK < firstK) listOf(low) else (firstK..lastK).map { it * step }
    return PriceGrid(step, values)
}
