package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import kotlinx.datetime.LocalDateTime

/** Earliest and latest stored bar timestamps for a symbol+timeframe. */
data class BarCoverage(val first: LocalDateTime, val last: LocalDateTime)

interface MarketDataRepository {
    suspend fun upsertBars(bars: List<Bar>)
    suspend fun getCoverage(symbol: String, timeframe: Timeframe): BarCoverage?
    suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDateTime, to: LocalDateTime): List<Bar>
}
