package io.github.earlisreal.wickplot

import kotlinx.datetime.LocalDateTime

/**
 * One OHLCV bar the chart can draw. A structural interface (no behaviour, no Compose types) so any
 * host can let its own bar model implement it and pass `List<MyBar>` straight through — the chart
 * never copies the series. Consumers without a bar type of their own can use [OhlcvCandle].
 */
interface Candle {
    val timestamp: LocalDateTime
    val open: Double
    val high: Double
    val low: Double
    val close: Double
    val volume: Long
}

/** Plain [Candle] for callers that don't have their own bar type (and for tests/samples). */
data class OhlcvCandle(
    override val timestamp: LocalDateTime,
    override val open: Double,
    override val high: Double,
    override val low: Double,
    override val close: Double,
    override val volume: Long,
) : Candle

/** A trade fill drawn as a diamond at an exact price on a given bar. */
data class PriceMarker(val barIndex: Int, val price: Double, val isBuy: Boolean)

/** One point on an overlay line (e.g. VWAP or a moving average), anchored to a bar index at a value. */
data class LinePoint(val barIndex: Int, val value: Double)
