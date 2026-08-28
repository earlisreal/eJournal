package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtapeMarketDataImporterTest {

    private var source: File? = null

    @AfterTest
    fun cleanup() {
        source?.delete()
    }

    @Test
    fun `imports only a position date and converts eTape timestamps`() = runTest {
        val dbFile = newSourceDatabase()
        insert(dbFile, "US.AAPL", "2026-06-10T04:00:00", 99.0, 100.0, 98.0, 99.5, 500L)
        insert(dbFile, "US.AAPL", "2026-06-10T09:30:00", 100.0, 101.0, 99.0, 100.5, 1_000L)
        insert(dbFile, "US.AAPL", "2026-06-10T16:00:00", 101.0, 102.0, 100.0, 101.5, 2_000L)
        insert(dbFile, "US.AAPL", "2026-06-10T20:00:00", 101.5, 102.5, 101.0, 102.0, 500L)
        insert(dbFile, "US.AAPL", "2026-06-11T09:30:00", 102.0, 103.0, 101.0, 102.5, 3_000L)

        val repository = RecordingMarketDataRepository()
        val result = EtapeMarketDataImporterImpl(repository, configuredPath = { dbFile.absolutePath })
            .importFor(listOf(position()))

        assertEquals(4, result.importedBars)
        assertEquals(4, repository.stored.size)
        assertEquals(Timeframe.TEN_SECONDS, repository.stored[0].timeframe)
        assertEquals(LocalDateTime.parse("2026-06-10T04:00"), repository.stored[0].timestamp)
        assertEquals(102.0, repository.stored[3].close)
        assertNull(result.warning)
    }

    @Test
    fun `skips unaligned and invalid source rows`() = runTest {
        val dbFile = newSourceDatabase()
        insert(dbFile, "US.AAPL", "2026-06-10T09:30:00", 100.0, 101.0, 99.0, 100.5, 1_000L)
        val aligned = LocalDateTime.parse("2026-06-10T09:30:00")
            .toInstant(TimeZone.of("America/New_York"))
            .toEpochMilliseconds()
        insertRaw(dbFile, "US.AAPL", aligned + 1L, 100.0, 101.0, 99.0, 100.5, 1_000L)
        insertRaw(dbFile, "US.AAPL", aligned + 10_000L, 100.0, 101.0, 99.0, 100.5, -1L)

        val repository = RecordingMarketDataRepository()
        val result = EtapeMarketDataImporterImpl(repository, configuredPath = { dbFile.absolutePath })
            .importFor(listOf(position()))

        assertEquals(1, result.importedBars)
        assertEquals(2, result.skippedBars)
        assertEquals(1, repository.stored.size)
    }

    @Test
    fun `missing configured source reports a warning while missing default stays quiet`() = runTest {
        val missing = File("${System.getProperty("java.io.tmpdir")}/missing-etape-${System.nanoTime()}.db")
        val repository = RecordingMarketDataRepository()
        val configured = EtapeMarketDataImporterImpl(repository, configuredPath = { missing.absolutePath })
            .importFor(listOf(position()))
        val default = EtapeMarketDataImporterImpl(repository, defaultPath = { missing })
            .importFor(listOf(position()))

        assertTrue(configured.warning?.contains("not found") == true)
        assertNull(default.warning)
    }

    private fun newSourceDatabase(): File {
        val file = Files.createTempFile("etape-bars-", ".db").toFile()
        source = file
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE bars_10s (
                        symbol TEXT NOT NULL,
                        ts INTEGER NOT NULL,
                        o REAL,
                        h REAL,
                        l REAL,
                        c REAL,
                        v INTEGER,
                        PRIMARY KEY(symbol, ts)
                    )
                    """.trimIndent(),
                )
            }
        }
        return file
    }

    private fun insert(
        file: File,
        symbol: String,
        timestamp: String,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Long,
    ) {
        val epochMillis = LocalDateTime.parse(timestamp)
            .toInstant(TimeZone.of("America/New_York"))
            .toEpochMilliseconds()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.prepareStatement("INSERT INTO bars_10s VALUES (?, ?, ?, ?, ?, ?, ?)").use { statement ->
                statement.setString(1, symbol)
                statement.setLong(2, epochMillis)
                statement.setDouble(3, open)
                statement.setDouble(4, high)
                statement.setDouble(5, low)
                statement.setDouble(6, close)
                statement.setLong(7, volume)
                statement.executeUpdate()
            }
        }
    }

    private fun insertRaw(
        file: File,
        symbol: String,
        epochMillis: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Long,
    ) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.prepareStatement("INSERT INTO bars_10s VALUES (?, ?, ?, ?, ?, ?, ?)").use { statement ->
                statement.setString(1, symbol)
                statement.setLong(2, epochMillis)
                statement.setDouble(3, open)
                statement.setDouble(4, high)
                statement.setDouble(5, low)
                statement.setDouble(6, close)
                statement.setLong(7, volume)
                statement.executeUpdate()
            }
        }
    }

    private fun position() = ClosedPosition(
        symbol = "AAPL",
        entryDatetime = LocalDateTime.parse("2026-06-10T09:30:05"),
        exitDatetime = LocalDateTime.parse("2026-06-10T10:00:05"),
        averageEntryPrice = 100.0,
        averageExitPrice = 101.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 10.0,
        transactions = listOf(
            Transaction(1L, 1L, "AAPL", LocalDateTime.parse("2026-06-10T09:30:05"), Action.BUY, 100.0, 10.0, 0.5),
            Transaction(2L, 1L, "AAPL", LocalDateTime.parse("2026-06-10T10:00:05"), Action.SELL, 101.0, 10.0, 0.5),
        ),
        market = Market.US_STOCKS,
    )

    private class RecordingMarketDataRepository : MarketDataRepository {
        val stored = mutableListOf<Bar>()

        override suspend fun upsertBars(market: Market, bars: List<Bar>) {
            stored += bars
        }

        override suspend fun getCoverage(symbol: String, timeframe: Timeframe, market: Market): BarCoverage? = null

        override suspend fun getBars(
            symbol: String,
            timeframe: Timeframe,
            market: Market,
            from: LocalDateTime,
            to: LocalDateTime,
        ): List<Bar> = error("unused")
    }
}
