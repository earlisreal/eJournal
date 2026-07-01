package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not a unit test in the strict sense — it's the screenshot harness for the Canvas-chart spike. It
 * renders the real [drawCandlestickChart] output (dark + light) to PNGs under docs/spikes/ so the
 * sample can be eyeballed without launching a JCEF-free headed run. Kept as a @Test so it runs via
 * `./gradlew :shared:jvmTest --tests "*CanvasChartSampleRenderTest"`.
 */
class CanvasChartSampleRenderTest {

    @Test
    fun `render dark and light sample charts to PNG`() {
        val bars = sampleBars()
        val window = BarWindow.initial(bars.size, maxBars = 120)
        val viewport = ChartViewport.fit(bars, window)
        // A couple of trade fills inside the visible window (entry long, exit).
        val markers = listOf(
            PriceMarker(barIndex = 72, price = bars[72].low, isBuy = true),
            PriceMarker(barIndex = 112, price = bars[112].high, isBuy = false),
        )
        val crosshair = 96

        val outDir = File("../docs/spikes/canvas-chart").apply { mkdirs() }
        val dark = renderPng(1100, 620, scale = 2f) {
            drawCandlestickChart(bars, markers, viewport, ChartColors.Dark, it, "ACME · D", crosshair)
        }
        val light = renderPng(1100, 620, scale = 2f) {
            drawCandlestickChart(bars, markers, viewport, ChartColors.Light, it, "ACME · D", crosshair)
        }
        File(outDir, "sample-dark.png").writeBytes(dark)
        File(outDir, "sample-light.png").writeBytes(light)

        // Intraday variant exercising the VWAP overlay + HH:MM time axis.
        val minuteBars = minuteSampleBars()
        val vwap = vwapLineFor(minuteBars)
        val ivWindow = BarWindow.initial(minuteBars.size, maxBars = 120)
        val ivViewport = ChartViewport.fit(minuteBars, ivWindow)
        val ivMarkers = listOf(
            PriceMarker(barIndex = 38, price = minuteBars[38].low, isBuy = true),
            PriceMarker(barIndex = 92, price = minuteBars[92].high, isBuy = false),
        )
        val intraday = renderPng(1100, 620, scale = 2f) {
            drawCandlestickChart(minuteBars, ivMarkers, ivViewport, ChartColors.Dark, it, "ACME · 1m", 70, vwap, true)
        }
        File(outDir, "sample-intraday-vwap.png").writeBytes(intraday)

        assertTrue(dark.size > 1000, "dark PNG should have real content")
        assertTrue(light.size > 1000, "light PNG should have real content")
        assertTrue(intraday.size > 1000, "intraday PNG should have real content")
    }

    /** Deterministic random-walk daily OHLCV so the sample is stable across runs. */
    private fun sampleBars(): List<Bar> {
        val rnd = Random(42)
        val bars = ArrayList<Bar>(160)
        var price = 100.0
        var date = LocalDate(2025, 6, 2)
        repeat(160) {
            val open = price
            val close = (open + (rnd.nextDouble() - 0.47) * 3.0).coerceAtLeast(5.0)
            val high = maxOf(open, close) + rnd.nextDouble() * 1.8
            val low = (minOf(open, close) - rnd.nextDouble() * 1.8).coerceAtLeast(1.0)
            val volume = (600_000 + rnd.nextInt(2_200_000)).toLong()
            bars.add(Bar("ACME", Timeframe.DAILY, LocalDateTime(date, LocalTime(0, 0)), open, high, low, close, volume))
            price = close
            date = date.plus(DatePeriod(days = 1))
        }
        return bars
    }

    /** Deterministic one-minute bars starting 09:30, for the intraday sample. */
    private fun minuteSampleBars(): List<Bar> {
        val rnd = Random(7)
        val bars = ArrayList<Bar>(180)
        var price = 50.0
        val day = LocalDate(2025, 6, 2)
        repeat(180) { i ->
            val open = price
            val close = (open + (rnd.nextDouble() - 0.48) * 0.6).coerceAtLeast(1.0)
            val high = maxOf(open, close) + rnd.nextDouble() * 0.35
            val low = (minOf(open, close) - rnd.nextDouble() * 0.35).coerceAtLeast(0.5)
            val volume = (40_000 + rnd.nextInt(180_000)).toLong()
            val t = LocalTime(9 + (30 + i) / 60, (30 + i) % 60)
            bars.add(Bar("ACME", Timeframe.ONE_MINUTE, LocalDateTime(day, t), open, high, low, close, volume))
            price = close
        }
        return bars
    }

    /** Cumulative typical-price VWAP, one point per bar. */
    private fun vwapLineFor(bars: List<Bar>): List<LinePoint> {
        var tpv = 0.0
        var vol = 0.0
        return bars.mapIndexed { i, b ->
            val typical = (b.high + b.low + b.close) / 3.0
            tpv += typical * b.volume
            vol += b.volume
            LinePoint(barIndex = i, value = if (vol > 0) tpv / vol else b.close)
        }
    }

    private fun renderPng(width: Int, height: Int, scale: Float, block: DrawScope.(TextMeasurer) -> Unit): ByteArray {
        val pxW = (width * scale).toInt()
        val pxH = (height * scale).toInt()
        val surface = Surface.makeRasterN32Premul(pxW, pxH)
        val density = Density(scale)
        val textMeasurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = surface.canvas.asComposeCanvas(),
            size = Size(pxW.toFloat(), pxH.toFloat()),
        ) {
            block(textMeasurer)
        }
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        return data.bytes
    }
}
