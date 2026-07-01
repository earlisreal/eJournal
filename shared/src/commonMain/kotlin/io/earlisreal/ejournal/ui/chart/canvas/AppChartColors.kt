package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.ui.graphics.Color
import io.earlisreal.chart.canvas.ChartColors
import io.earlisreal.ejournal.ui.theme.AppColors

/**
 * Bridges the app theme onto the chart library's [ChartColors]. Kept in the app (not the library) so
 * the library stays theme-agnostic; the trade-marker diamonds are fixed pastels, not theme colours.
 */
fun chartColorsFrom(c: AppColors): ChartColors = ChartColors(
    background = c.contentBackground,
    grid = c.textMuted.copy(alpha = 0.12f),
    up = c.candleUp,
    down = c.candleDown,
    axisText = c.textMuted,
    axisLine = c.border,
    crosshair = c.textPrimary.copy(alpha = 0.55f),
    legendBg = c.surface.copy(alpha = 0.92f),
    legendText = c.textPrimary,
    // Pastel green / pink diamonds (theme-independent). The 0.8 fill alpha + dark border are applied
    // by the renderer.
    buyMarker = Color(0xFFA5D6A7),
    sellMarker = Color(0xFFF48FB1),
    // Punchier profit-green (not the muted candleUp) so the overlay line reads distinctly over the
    // up-candles rather than blending in.
    vwap = c.profit,
)
