package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.LocalDateTime

/** Earliest and latest stored bar timestamps for a symbol+timeframe. */
data class BarCoverage(val first: LocalDateTime, val last: LocalDateTime)

interface MarketDataRepository {
    suspend fun upsertBars(market: Market, bars: List<Bar>)
    suspend fun getCoverage(symbol: String, timeframe: Timeframe, market: Market): BarCoverage?
    suspend fun getBars(symbol: String, timeframe: Timeframe, market: Market, from: LocalDateTime, to: LocalDateTime): List<Bar>
}
