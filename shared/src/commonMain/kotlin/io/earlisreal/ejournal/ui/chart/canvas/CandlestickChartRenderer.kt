package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.earlisreal.ejournal.domain.marketdata.Bar
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.math.round

// Shared layout metrics so the composable's pointer math and the renderer agree on the plot rect.
internal val RIGHT_AXIS_DP = 56.dp
internal val BOTTOM_AXIS_DP = 22.dp
internal val TOP_PAD_DP = 8.dp

// Trade-diamond fill opacity — matches the old Lightweight-Charts markers (rgba .8).
private const val MARKER_ALPHA = 0.8f

/**
 * Draws the whole candlestick chart (grid, volume, candles, axes, trade diamonds, crosshair, legend)
 * onto a [DrawScope]. This is the single source of truth for the visual: the live
 * [CandlestickCanvasChart] composable calls it inside a `Canvas {}`, and the screenshot harness calls
 * it against an offscreen Skia surface — so what you see in the sample PNG is exactly what the app
 * renders.
 */
fun DrawScope.drawCandlestickChart(
    bars: List<Bar>,
    markers: List<PriceMarker>,
    viewport: ChartViewport,
    colors: ChartColors,
    textMeasurer: TextMeasurer,
    title: String,
    crosshairBar: Int? = null,
    vwap: List<LinePoint> = emptyList(),
    intraday: Boolean = false,
) {
    drawRect(colors.background, size = size)
    if (bars.isEmpty() || viewport.visibleBars <= 0) return

    val rightAxis = RIGHT_AXIS_DP.toPx()
    val bottomAxis = BOTTOM_AXIS_DP.toPx()
    val topPad = TOP_PAD_DP.toPx()
    val plotLeft = 0f
    val plotWidth = (size.width - rightAxis).coerceAtLeast(1f)
    val plotTop = topPad
    val plotHeight = (size.height - bottomAxis - topPad).coerceAtLeast(1f)
    val plotBottom = plotTop + plotHeight
    val volumeHeight = plotHeight * 0.18f

    val axisStyle = TextStyle(color = colors.axisText, fontSize = 10.sp)

    // ── Price grid + right-axis labels ──
    val levels = 4
    for (i in 0..levels) {
        val price = viewport.priceHigh - (viewport.priceHigh - viewport.priceLow) * i / levels
        val y = viewport.priceToY(price, plotTop, plotHeight)
        drawLine(colors.grid, Offset(plotLeft, y), Offset(plotLeft + plotWidth, y), strokeWidth = 1f)
        val layout = textMeasurer.measure(fmt2(price), axisStyle)
        drawText(layout, topLeft = Offset(size.width - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
    }
    drawLine(colors.axisLine, Offset(plotLeft + plotWidth, plotTop), Offset(plotLeft + plotWidth, plotBottom), strokeWidth = 1f)

    val start = viewport.startIndex
    val end = (start + viewport.visibleBars).coerceAtMost(bars.size)
    val slot = viewport.slotWidth(plotWidth)
    val bodyW = (slot * 0.7f).coerceAtLeast(1f)
    val wickW = (slot * 0.12f).coerceIn(1f, 3f)

    // ── Volume (bottom band, semi-transparent, drawn under the candles) ──
    for (i in start until end) {
        val b = bars[i]
        val x = viewport.xCenter(i, plotLeft, plotWidth)
        val h = viewport.volumeToHeight(b.volume, volumeHeight)
        val col = (if (b.close >= b.open) colors.up else colors.down).copy(alpha = 0.35f)
        drawRect(col, topLeft = Offset(x - bodyW / 2f, plotBottom - h), size = Size(bodyW, h))
    }

    // ── Candles ──
    for (i in start until end) {
        val b = bars[i]
        val x = viewport.xCenter(i, plotLeft, plotWidth)
        val col = if (b.close >= b.open) colors.up else colors.down
        drawLine(
            col,
            Offset(x, viewport.priceToY(b.high, plotTop, plotHeight)),
            Offset(x, viewport.priceToY(b.low, plotTop, plotHeight)),
            strokeWidth = wickW,
        )
        val top = viewport.priceToY(maxOf(b.open, b.close), plotTop, plotHeight)
        val bot = viewport.priceToY(minOf(b.open, b.close), plotTop, plotHeight)
        drawRect(col, topLeft = Offset(x - bodyW / 2f, top), size = Size(bodyW, (bot - top).coerceAtLeast(1f)))
    }

    // ── VWAP overlay (clipped to the plot so it never paints over the axis) ──
    if (vwap.isNotEmpty()) {
        clipRect(left = plotLeft, top = plotTop, right = plotLeft + plotWidth, bottom = plotBottom) {
            var prev: LinePoint? = null
            for (p in vwap) {
                val a = prev
                if (a != null && (a.barIndex in start until end || p.barIndex in start until end)) {
                    drawLine(
                        colors.vwap,
                        Offset(viewport.xCenter(a.barIndex, plotLeft, plotWidth), viewport.priceToY(a.value, plotTop, plotHeight)),
                        Offset(viewport.xCenter(p.barIndex, plotLeft, plotWidth), viewport.priceToY(p.value, plotTop, plotHeight)),
                        strokeWidth = 1.5f,
                    )
                }
                prev = p
            }
        }
    }

    // ── Time axis labels (HH:MM intraday, MM-DD daily/weekly) ──
    val ticks = 4
    for (i in 0..ticks) {
        val idx = start + (end - 1 - start) * i / ticks
        if (idx !in start until end) continue
        val x = viewport.xCenter(idx, plotLeft, plotWidth)
        val layout = textMeasurer.measure(timeLabel(bars[idx].timestamp, intraday), axisStyle)
        val lx = (x - layout.size.width / 2f).coerceIn(0f, plotWidth - layout.size.width)
        drawText(layout, topLeft = Offset(lx, plotBottom + 3.dp.toPx()))
    }

    // ── Trade markers (exact-price diamonds) ──
    val r = 6.dp.toPx()
    for (mk in markers) {
        if (mk.barIndex !in start until end) continue
        val x = viewport.xCenter(mk.barIndex, plotLeft, plotWidth)
        val y = viewport.priceToY(mk.price, plotTop, plotHeight)
        val path = Path().apply {
            moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close()
        }
        drawPath(path, (if (mk.isBuy) colors.buyMarker else colors.sellMarker).copy(alpha = MARKER_ALPHA))
        drawPath(path, Color(0x8C000000), style = Stroke(width = 1f))
    }

    // ── Crosshair + price tag ──
    if (crosshairBar != null && crosshairBar in start until end) {
        val b = bars[crosshairBar]
        val x = viewport.xCenter(crosshairBar, plotLeft, plotWidth)
        val y = viewport.priceToY(b.close, plotTop, plotHeight)
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        drawLine(colors.crosshair, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1f, pathEffect = dash)
        drawLine(colors.crosshair, Offset(plotLeft, y), Offset(plotLeft + plotWidth, y), strokeWidth = 1f, pathEffect = dash)
        val tagStyle = TextStyle(color = colors.background, fontSize = 10.sp)
        val tag = textMeasurer.measure(fmt2(b.close), tagStyle)
        val tagH = tag.size.height + 4.dp.toPx()
        drawRect(colors.crosshair, topLeft = Offset(plotLeft + plotWidth, y - tagH / 2f), size = Size(rightAxis, tagH))
        drawText(tag, topLeft = Offset(plotLeft + plotWidth + 4.dp.toPx(), y - tag.size.height / 2f))
    }

    val vwapAtCrosshair = crosshairBar?.let { cb -> vwap.firstOrNull { it.barIndex == cb }?.value }
    drawLegend(textMeasurer, colors, title, crosshairBar?.let { bars.getOrNull(it) }, vwapAtCrosshair)
}

private fun DrawScope.drawLegend(tm: TextMeasurer, colors: ChartColors, title: String, bar: Bar?, vwapValue: Double?) {
    val titleStyle = TextStyle(color = colors.legendText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val lineStyle = TextStyle(color = colors.legendText, fontSize = 11.sp)
    val lines = buildList {
        add(tm.measure(title, titleStyle))
        if (bar != null) {
            val col = if (bar.close >= bar.open) colors.up else colors.down
            add(tm.measure("O ${fmt2(bar.open)}  H ${fmt2(bar.high)}  L ${fmt2(bar.low)}  C ${fmt2(bar.close)}", lineStyle.copy(color = col)))
            add(tm.measure("Vol ${compactVol(bar.volume)}", lineStyle))
        }
        if (vwapValue != null) add(tm.measure("VWAP ${fmt2(vwapValue)}", lineStyle.copy(color = colors.vwap)))
    }
    val pad = 8.dp.toPx()
    val gap = 2.dp.toPx()
    val boxW = lines.maxOf { it.size.width } + pad * 2
    val boxH = lines.sumOf { it.size.height } + pad * 2 + gap * (lines.size - 1)
    drawRoundRect(colors.legendBg, topLeft = Offset(pad, pad), size = Size(boxW, boxH), cornerRadius = CornerRadius(6.dp.toPx()))
    var y = pad * 2
    for (layout in lines) {
        drawText(layout, topLeft = Offset(pad * 2, y))
        y += layout.size.height + gap
    }
}

private fun timeLabel(ts: LocalDateTime, intraday: Boolean): String =
    if (intraday) "${pad2(ts.hour)}:${pad2(ts.minute)}" else ts.date.toString().substring(5) // MM-DD

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

// ── kotlinx-common number formatting (no String.format in commonMain) ──

private fun fmt2(v: Double): String {
    val scaled = round(abs(v) * 100).toLong()
    return (if (v < 0) "-" else "") + "${scaled / 100}." + (scaled % 100).toString().padStart(2, '0')
}

private fun compactVol(v: Long): String = when {
    v >= 1_000_000_000 -> "${fmt1(v / 1e9)}B"
    v >= 1_000_000 -> "${fmt1(v / 1e6)}M"
    v >= 1_000 -> "${fmt1(v / 1e3)}K"
    else -> v.toString()
}

private fun fmt1(v: Double): String {
    val scaled = round(v * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}
