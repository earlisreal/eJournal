package io.earlisreal.ejournal.ui.chart.canvas

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CandlestickChartMathTest {

    private fun bar(o: Double, h: Double, l: Double, c: Double, v: Long) =
        Bar("T", Timeframe.DAILY, LocalDateTime(2026, 1, 1, 0, 0), o, h, l, c, v)

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
}
