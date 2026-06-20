package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.earlisreal.ejournal.domain.analytics.EquityPoint
import io.earlisreal.ejournal.ui.theme.AppTheme

/** Vertical padding as a fraction of the canvas height; shared by the curve math and label placement. */
private const val PAD_FRACTION = 0.10f

/**
 * Cumulative-P&L line drawn natively on a Compose [Canvas] (no WebView). The line and its area
 * fill run to the zero baseline; the chart is clipped at that baseline and drawn twice so the
 * region above zero is green and the region below zero is red. The peak/floor (and $0 when the
 * curve straddles zero) are labelled on the left axis, and the first/middle/last trade dates run
 * along the bottom. X spacing is per-trade.
 */
@Composable
fun EquityCurveChart(points: List<EquityPoint>, symbol: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No closed trades in range", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val values = points.map { it.cumulative }
    val lo = minOf(0.0, values.min())
    val hi = maxOf(0.0, values.max())
    val profit = colors.profit
    val loss = colors.loss
    val baselineColor = colors.border

    Column(modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padV = h * PAD_FRACTION
                val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0

                fun yOf(v: Double): Float = (padV + (1.0 - (v - lo) / span) * (h - 2 * padV)).toFloat()
                fun xOf(i: Int): Float = if (points.size == 1) w / 2f else w * i / (points.size - 1)

                val zeroY = yOf(0.0)

                // Dashed zero baseline (break-even reference)
                drawLine(
                    color = baselineColor,
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )

                if (points.size == 1) {
                    drawCircle(if (values[0] >= 0.0) profit else loss, radius = 3f, center = Offset(xOf(0), yOf(values[0])))
                    return@Canvas
                }

                val linePath = Path().apply {
                    moveTo(xOf(0), yOf(values[0]))
                    for (i in 1 until points.size) lineTo(xOf(i), yOf(values[i]))
                }
                // Fill polygon runs from the line down to the zero baseline. Clipping at zeroY
                // separates the above-zero (green) and below-zero (red) regions automatically.
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(xOf(points.lastIndex), zeroY)
                    lineTo(xOf(0), zeroY)
                    close()
                }

                // Above zero → green
                clipRect(top = 0f, bottom = zeroY) {
                    drawPath(fillPath, profit.copy(alpha = 0.15f))
                    drawPath(linePath, profit, style = Stroke(width = 2f))
                }
                // Below zero → red
                clipRect(top = zeroY, bottom = h) {
                    drawPath(fillPath, loss.copy(alpha = 0.15f))
                    drawPath(linePath, loss, style = Stroke(width = 2f))
                }
            }

            // Value labels for the chart's vertical extent (peak at top, floor at bottom).
            Text(
                compactMoney(hi, symbol),
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp),
            )
            Text(
                compactMoney(lo, symbol),
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp),
            )
            // Zero baseline label — only when the curve straddles zero, otherwise the top or bottom
            // label already sits at $0.
            if (lo < 0.0 && hi > 0.0) {
                val span = hi - lo
                val zeroFraction = PAD_FRACTION + (1.0 - (0.0 - lo) / span) * (1.0 - 2.0 * PAD_FRACTION)
                Text(
                    compactMoney(0.0, symbol),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = -1f, verticalBias = (zeroFraction * 2.0 - 1.0).toFloat()))
                        .padding(start = 2.dp),
                )
            }
        }

        // Date axis: first / middle / last trade dates, aligned under their x positions.
        val dates = points.map { it.datetime.date }
        val first = dates.first()
        val last = dates.last()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = if (first == last) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            if (first == last) {
                DateLabel(shortDate(first), colors.textMuted)
            } else {
                DateLabel(shortDate(first), colors.textMuted)
                val mid = dates[dates.size / 2]
                if (points.size >= 3 && mid != first && mid != last) DateLabel(shortDate(mid), colors.textMuted)
                DateLabel(shortDate(last), colors.textMuted)
            }
        }
    }
}

@Composable
private fun DateLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall)
}
