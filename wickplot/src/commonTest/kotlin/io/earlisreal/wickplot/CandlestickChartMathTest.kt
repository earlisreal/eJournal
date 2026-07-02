package io.earlisreal.wickplot

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CandlestickChartMathTest {

    private fun bar(o: Double, h: Double, l: Double, c: Double, v: Long) =
        OhlcvCandle(LocalDateTime(2026, 1, 1, 0, 0), o, h, l, c, v)

    // ── BarWindow ────────────────────────────────────────────────────────────

    @Test
    fun `initial window shows the last maxBars when data exceeds it`() {
        val w = BarWindow.initial(totalBars = 500, maxBars = 120)
        assertEquals(380, w.startIndex)
        assertEquals(120, w.visibleBars)
    }

    @Test
    fun `initial window shows all bars when data is smaller than maxBars`() {
        val w = BarWindow.initial(totalBars = 40, maxBars = 120)
        assertEquals(0, w.startIndex)
        assertEquals(40, w.visibleBars)
    }

    @Test
    fun `pan shifts the start index by whole bars`() {
        val w = BarWindow(startIndex = 100, visibleBars = 50).pan(deltaBars = -10, totalBars = 500)
        assertEquals(90, w.startIndex)
        assertEquals(50, w.visibleBars)
    }

    @Test
    fun `pan clamps at the left edge`() {
        val w = BarWindow(startIndex = 5, visibleBars = 50).pan(deltaBars = -20, totalBars = 500)
        assertEquals(0, w.startIndex)
    }

    @Test
    fun `pan clamps at the right edge so the window stays within data`() {
        val w = BarWindow(startIndex = 460, visibleBars = 50).pan(deltaBars = 100, totalBars = 500)
        assertEquals(450, w.startIndex) // 500 - 50
    }

    @Test
    fun `zoom in reduces visible bars and keeps the left-edge bar anchored`() {
        val w = BarWindow(startIndex = 100, visibleBars = 40).zoom(factor = 0.5, focusFraction = 0.0, totalBars = 500)
        assertEquals(20, w.visibleBars)
        assertEquals(100, w.startIndex)
    }

    @Test
    fun `zoom keeps the bar under the cursor fixed`() {
        // center bar = 100 + 0.5*40 = 120; newVisible = 20; start = 120 - 0.5*20 = 110
        val w = BarWindow(startIndex = 100, visibleBars = 40).zoom(factor = 0.5, focusFraction = 0.5, totalBars = 500)
        assertEquals(20, w.visibleBars)
        assertEquals(110, w.startIndex)
    }

    @Test
    fun `zoom in clamps to a minimum number of bars`() {
        val w = BarWindow(startIndex = 100, visibleBars = 8).zoom(factor = 0.1, focusFraction = 0.5, totalBars = 500)
        assertEquals(BarWindow.MIN_BARS, w.visibleBars)
    }

    @Test
    fun `zoom out clamps to the total bar count`() {
        val w = BarWindow(startIndex = 0, visibleBars = 80).zoom(factor = 10.0, focusFraction = 0.0, totalBars = 100)
        assertEquals(100, w.visibleBars)
        assertEquals(0, w.startIndex)
    }

    // ── ChartViewport.fit ────────────────────────────────────────────────────

    @Test
    fun `fit derives a padded price range and max volume from the visible bars`() {
        val bars = listOf(
            bar(10.0, 12.0, 9.0, 11.0, 100),
            bar(11.0, 15.0, 10.0, 14.0, 200),
            bar(14.0, 14.5, 8.0, 9.0, 300),
        )
        val vp = ChartViewport.fit(bars, BarWindow(0, 3), padFraction = 0.10)
        // low = 8, high = 15, span = 7, pad = 0.7
        assertEquals(7.3, vp.priceLow, 1e-9)
        assertEquals(15.7, vp.priceHigh, 1e-9)
        assertEquals(300.0, vp.maxVolume, 1e-9)
    }

    // ── Pixel mappings ───────────────────────────────────────────────────────

    @Test
    fun `priceToY maps high to the top and low to the bottom`() {
        val vp = ChartViewport(startIndex = 0, visibleBars = 3, priceLow = 0.0, priceHigh = 100.0, maxVolume = 1.0)
        assertEquals(0f, vp.priceToY(100.0, plotTop = 0f, plotHeight = 200f), 1e-3f)
        assertEquals(200f, vp.priceToY(0.0, plotTop = 0f, plotHeight = 200f), 1e-3f)
        assertEquals(100f, vp.priceToY(50.0, plotTop = 0f, plotHeight = 200f), 1e-3f)
    }

    @Test
    fun `yToPrice inverts priceToY so the crosshair reads the price under the cursor`() {
        val vp = ChartViewport(startIndex = 0, visibleBars = 3, priceLow = 0.0, priceHigh = 100.0, maxVolume = 1.0)
        assertEquals(100.0, vp.yToPrice(0f, plotTop = 0f, plotHeight = 200f), 1e-6)
        assertEquals(0.0, vp.yToPrice(200f, plotTop = 0f, plotHeight = 200f), 1e-6)
        assertEquals(50.0, vp.yToPrice(100f, plotTop = 0f, plotHeight = 200f), 1e-6)
        // Round-trips against priceToY for an arbitrary price with a non-zero plotTop.
        val y = vp.priceToY(42.0, plotTop = 8f, plotHeight = 200f)
        assertEquals(42.0, vp.yToPrice(y, plotTop = 8f, plotHeight = 200f), 1e-4)
    }

    @Test
    fun `xCenter places the first visible bar half a slot from the left`() {
        val vp = ChartViewport(startIndex = 10, visibleBars = 10, priceLow = 0.0, priceHigh = 1.0, maxVolume = 1.0)
        assertEquals(5f, vp.xCenter(10, plotLeft = 0f, plotWidth = 100f), 1e-3f)
        assertEquals(15f, vp.xCenter(11, plotLeft = 0f, plotWidth = 100f), 1e-3f)
    }

    @Test
    fun `barIndexAt is safe on an empty viewport (no bars loaded yet)`() {
        // Reproduces the crash when the crosshair moves over the chart before data loads:
        // an empty fit() yields visibleBars=0, so the clamp range [0, -1] is invalid.
        val vp = ChartViewport(startIndex = 0, visibleBars = 0, priceLow = 0.0, priceHigh = 1.0, maxVolume = 1.0)
        assertEquals(0, vp.barIndexAt(50f, plotLeft = 0f, plotWidth = 100f))
    }

    @Test
    fun `barIndexAt inverts xCenter and clamps to the visible window`() {
        val vp = ChartViewport(startIndex = 10, visibleBars = 10, priceLow = 0.0, priceHigh = 1.0, maxVolume = 1.0)
        assertEquals(10, vp.barIndexAt(5f, plotLeft = 0f, plotWidth = 100f))
        assertEquals(11, vp.barIndexAt(15f, plotLeft = 0f, plotWidth = 100f))
        assertEquals(19, vp.barIndexAt(999f, plotLeft = 0f, plotWidth = 100f)) // clamp to last visible
    }

    // ── Nice-number price axis (TradingView-style) ───────────────────────────

    @Test
    fun `niceAxisStep rounds up to 1, 2, 2_5, 5 times a power of ten`() {
        assertEquals(0.05, niceAxisStep(0.03), 1e-9)   // 3 → 5  (×0.01)
        assertEquals(0.10, niceAxisStep(0.06), 1e-9)   // 6 → 10 (×0.01)
        assertEquals(0.20, niceAxisStep(0.20), 1e-9)   // 2 → 2
        assertEquals(1.0, niceAxisStep(1.0), 1e-9)     // 1 → 1
        assertEquals(5.0, niceAxisStep(3.0), 1e-9)     // 3 → 5
        assertEquals(10.0, niceAxisStep(7.0), 1e-9)    // 7 → 10
        assertEquals(25.0, niceAxisStep(21.0), 1e-9)   // 2.1 → 2.5 (×10)
    }

    @Test
    fun `axisDecimals reports the digits needed to print a step exactly`() {
        assertEquals(2, axisDecimals(0.05))
        assertEquals(1, axisDecimals(0.5))
        assertEquals(1, axisDecimals(2.5))
        assertEquals(0, axisDecimals(1.0))
        assertEquals(0, axisDecimals(10.0))
    }

    @Test
    fun `priceGrid snaps levels to round multiples of a nice step`() {
        val grid = priceGrid(107.87, 144.70, targetCount = 5)
        assertEquals(10.0, grid.step, 1e-9)
        // Round numbers strictly inside the range — not the padded high/low.
        assertEquals(4, grid.values.size)
        assertEquals(110.0, grid.values.first(), 1e-9)
        assertEquals(140.0, grid.values.last(), 1e-9)
    }

    @Test
    fun `priceGrid tightens the step for a narrow price range`() {
        val grid = priceGrid(9.98, 10.06, targetCount = 4)
        assertEquals(0.02, grid.step, 1e-9)
        assertEquals(2, axisDecimals(grid.step))
        assertEquals(9.98, grid.values.first(), 1e-9)
        assertEquals(10.06, grid.values.last(), 1e-9)
    }

    @Test
    fun `priceGrid is safe on a zero-width range`() {
        val grid = priceGrid(5.0, 5.0, targetCount = 5)
        assertEquals(1, grid.values.size)
        assertEquals(5.0, grid.values.first(), 1e-9)
    }
}
