package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.chart.canvas.Candle
import kotlinx.datetime.LocalDateTime

enum class Timeframe { DAILY, ONE_MINUTE }

/**
 * One OHLCV candle. Timestamps are exchange-local (America/New_York for US stocks). Implements the
 * chart library's [Candle] so a `List<Bar>` can be drawn directly with no copy.
 */
data class Bar(
    val symbol: String,
    val timeframe: Timeframe,
    override val timestamp: LocalDateTime,
    override val open: Double,
    override val high: Double,
    override val low: Double,
    override val close: Double,
    override val volume: Long,
) : Candle
