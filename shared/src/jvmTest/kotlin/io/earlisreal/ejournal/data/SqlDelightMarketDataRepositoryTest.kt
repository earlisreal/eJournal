package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightMarketDataRepositoryTest {

    private lateinit var repo: SqlDelightMarketDataRepository

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val db = AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter),
            OhlcvBarAdapter = io.earlisreal.ejournal.OhlcvBar.Adapter(
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
        repo = SqlDelightMarketDataRepository(db)
    }

    private fun bar(
        symbol: String = "AAPL",
        timeframe: Timeframe = Timeframe.DAILY,
        timestamp: String = "2026-06-01T00:00",
        close: Double = 100.0,
        volume: Long = 1_000L,
    ) = Bar(
        symbol = symbol,
        timeframe = timeframe,
        timestamp = LocalDateTime.parse(timestamp),
        open = 99.0, high = 101.0, low = 98.0, close = close,
        volume = volume,
    )

    @Test
    fun `upsert and query bars in range`() = runTest {
        repo.upsertBars(
            listOf(
                bar(timestamp = "2026-06-01T00:00"),
                bar(timestamp = "2026-06-02T00:00"),
                bar(timestamp = "2026-06-03T00:00"),
            )
        )
        val bars = repo.getBars(
            "AAPL", Timeframe.DAILY,
            from = LocalDateTime.parse("2026-06-01T00:00"),
            to = LocalDateTime.parse("2026-06-02T23:59"),
        )
        assertEquals(2, bars.size)
        assertEquals(LocalDateTime.parse("2026-06-01T00:00"), bars[0].timestamp)
    }

    @Test
    fun `upserting the same bar twice keeps one row with latest values`() = runTest {
        repo.upsertBars(listOf(bar(close = 100.0)))
        repo.upsertBars(listOf(bar(close = 105.0)))
        val bars = repo.getBars(
            "AAPL", Timeframe.DAILY,
            from = LocalDateTime.parse("2026-01-01T00:00"),
            to = LocalDateTime.parse("2026-12-31T00:00"),
        )
        assertEquals(1, bars.size)
        assertEquals(105.0, bars[0].close)
    }

    @Test
    fun `coverage returns min and max timestamps per symbol and timeframe`() = runTest {
        repo.upsertBars(
            listOf(
                bar(timestamp = "2026-06-02T00:00"),
                bar(timestamp = "2026-06-05T00:00"),
                bar(symbol = "TSLA", timestamp = "2026-01-01T00:00"),
                bar(timeframe = Timeframe.ONE_MINUTE, timestamp = "2026-06-10T09:30"),
            )
        )
        val coverage = repo.getCoverage("AAPL", Timeframe.DAILY)!!
        assertEquals(LocalDateTime.parse("2026-06-02T00:00"), coverage.first)
        assertEquals(LocalDateTime.parse("2026-06-05T00:00"), coverage.last)
    }

    @Test
    fun `coverage is null when no bars stored`() = runTest {
        assertNull(repo.getCoverage("AAPL", Timeframe.DAILY))
    }

    @Test
    fun `bars are isolated by timeframe`() = runTest {
        repo.upsertBars(
            listOf(
                bar(timeframe = Timeframe.DAILY, timestamp = "2026-06-01T00:00"),
                bar(timeframe = Timeframe.ONE_MINUTE, timestamp = "2026-06-01T09:30"),
            )
        )
        val daily = repo.getBars(
            "AAPL", Timeframe.DAILY,
            from = LocalDateTime.parse("2026-06-01T00:00"),
            to = LocalDateTime.parse("2026-06-01T23:59"),
        )
        assertEquals(1, daily.size)
        assertEquals(Timeframe.DAILY, daily[0].timeframe)
    }
}
