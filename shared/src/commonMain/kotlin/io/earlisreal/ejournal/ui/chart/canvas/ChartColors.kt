package io.earlisreal.ejournal.ui.chart.canvas

import androidx.compose.ui.graphics.Color
import io.earlisreal.ejournal.ui.theme.AppColors
import io.earlisreal.ejournal.ui.theme.darkAppColors
import io.earlisreal.ejournal.ui.theme.lightAppColors

/** A trade fill drawn as a diamond at an exact price on a given bar (mirrors the JCEF PriceDiamonds). */
data class PriceMarker(val barIndex: Int, val price: Double, val isBuy: Boolean)

/** One point on an overlay line (e.g. VWAP), anchored to a bar index at a price. */
data class LinePoint(val barIndex: Int, val value: Double)

/**
 * Flat colour set the renderer draws with. Kept separate from [AppColors] so the renderer stays
 * Compose-composition-free and can be exercised offscreen (the screenshot harness) with the [Dark]
 * / [Light] presets, while the live composable derives them from the app theme via [from].
 */
data class ChartColors(
    val background: Color,
    val grid: Color,
    val up: Color,
    val down: Color,
    val axisText: Color,
    val axisLine: Color,
    val crosshair: Color,
    val legendBg: Color,
    val legendText: Color,
    val buyMarker: Color,
    val sellMarker: Color,
    val vwap: Color,
) {
    companion object {
        fun from(c: AppColors): ChartColors = ChartColors(
            background = c.contentBackground,
            grid = c.textMuted.copy(alpha = 0.12f),
            up = c.profit,
            down = c.loss,
            axisText = c.textMuted,
            axisLine = c.border,
            crosshair = c.textPrimary.copy(alpha = 0.55f),
            legendBg = c.surface.copy(alpha = 0.92f),
            legendText = c.textPrimary,
            // Match the old Lightweight-Charts diamonds exactly: pastel green / pink (theme-independent).
            // The 0.8 fill alpha + dark border are applied by the renderer (see MARKER_ALPHA).
            buyMarker = Color(0xFFA5D6A7),
            sellMarker = Color(0xFFF48FB1),
            vwap = c.accent,
        )

        val Dark = from(darkAppColors)
        val Light = from(lightAppColors)
    }
}
