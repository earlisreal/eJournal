package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.LocalDateTime

enum class Timeframe { DAILY, ONE_MINUTE }

/** One OHLCV candle. Timestamps are exchange-local (America/New_York for US stocks). */
data class Bar(
    val symbol: String,
    val timeframe: Timeframe,
    val timestamp: LocalDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)
