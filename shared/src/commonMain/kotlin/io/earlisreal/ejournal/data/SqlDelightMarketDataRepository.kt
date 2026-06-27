package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.LocalDateTime

class SqlDelightMarketDataRepository(private val db: AppDatabase) : MarketDataRepository {

    override suspend fun upsertBars(market: Market, bars: List<Bar>) {
        db.ohlcvBarQueries.transaction {
            bars.forEach { bar ->
                db.ohlcvBarQueries.upsertBar(
                    symbol    = bar.symbol,
                    market    = market,
                    timeframe = bar.timeframe,
                    timestamp = bar.timestamp,
                    open_     = bar.open,
                    high      = bar.high,
                    low       = bar.low,
                    close     = bar.close,
                    volume    = bar.volume,
                )
            }
        }
    }

    override suspend fun getCoverage(symbol: String, timeframe: Timeframe, market: Market): BarCoverage? {
        val row = db.ohlcvBarQueries.selectCoverage(symbol, market, timeframe).executeAsOne()
        val first = row.first ?: return null
        val last = row.last ?: return null
        return BarCoverage(first, last)
    }

    override suspend fun getBars(
        symbol: String,
        timeframe: Timeframe,
        market: Market,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Bar> =
        db.ohlcvBarQueries.selectBarsInRange(symbol, market, timeframe, from, to).executeAsList().map { it.toDomain() }

    private fun io.earlisreal.ejournal.OhlcvBar.toDomain() = Bar(
        symbol    = symbol,
        timeframe = timeframe,
        timestamp = timestamp,
        open      = open_,
        high      = high,
        low       = low,
        close     = close,
        volume    = volume,
    )
}
