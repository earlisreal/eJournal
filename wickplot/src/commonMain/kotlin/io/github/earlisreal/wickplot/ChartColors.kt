package io.github.earlisreal.wickplot

import androidx.compose.ui.graphics.Color

/**
 * Flat colour set the renderer draws with. Kept as a plain struct (no Compose composition, no theme
 * dependency) so the renderer can be exercised offscreen with the [Dark]/[Light] presets, and hosts
 * can map their own theme onto it.
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
        /** Reasonable dark palette so the chart is usable with zero configuration. */
        val Dark = ChartColors(
            background = Color(0xFF0F1115),
            grid = Color(0xFF9AA3B2).copy(alpha = 0.12f),
            up = Color(0xFF4C9E82),
            down = Color(0xFFE0605E),
            axisText = Color(0xFF9AA3B2),
            axisLine = Color(0xFF2A2F3A),
            crosshair = Color(0xFFE6E9EF).copy(alpha = 0.55f),
            legendBg = Color(0xFF171A21).copy(alpha = 0.92f),
            legendText = Color(0xFFE6E9EF),
            buyMarker = Color(0xFFA5D6A7),
            sellMarker = Color(0xFFF48FB1),
            vwap = Color(0xFF3FB950),
        )

        /** Reasonable light palette so the chart is usable with zero configuration. */
        val Light = ChartColors(
            background = Color(0xFFFFFFFF),
            grid = Color(0xFF6B7180).copy(alpha = 0.12f),
            up = Color(0xFF2E9E74),
            down = Color(0xFFD46A62),
            axisText = Color(0xFF6B7180),
            axisLine = Color(0xFFE7E9F0),
            crosshair = Color(0xFF1B1F2A).copy(alpha = 0.55f),
            legendBg = Color(0xFFFFFFFF).copy(alpha = 0.92f),
            legendText = Color(0xFF1B1F2A),
            buyMarker = Color(0xFFA5D6A7),
            sellMarker = Color(0xFFF48FB1),
            vwap = Color(0xFF16A34A),
        )
    }
}
