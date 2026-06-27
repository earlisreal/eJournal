package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.SqlDelightMarketDataRepository
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for the `SQLITE_BUSY: database is locked` crashes seen during market-data
 * sync: many symbols upsert bars concurrently (the provider semaphores only gate the network
 * fetch, not the DB write), and the file-backed driver pools connections. In the default
 * rollback-journal mode SQLite returns SQLITE_BUSY immediately on concurrent writers, ignoring
 * busy_timeout. WAL mode serializes writers via the write lock and honors busy_timeout instead.
 */
class JvmDatabaseFactoryConcurrencyTest {

    private fun tempDir(): File = Files.createTempDirectory("ejournal-db-concurrency").toFile()

    private fun JdbcSqliteDriver.pragma(name: String): String? = executeQuery(
        null,
        "PRAGMA $name",
        { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        0,
    ).value

    @Test
    fun `database is opened in WAL mode`() {
        val dir = tempDir()
        JvmDatabaseFactory.create(dir)

        // journal_mode is persisted in the file header, so any connection reports it.
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dir, "ejournal.db").absolutePath}")
        val mode = driver.pragma("journal_mode")
        driver.close()

        assertEquals("wal", mode)
    }

    @Test
    fun `configured driver applies an extended busy timeout`() {
        // busy_timeout is per-connection, so it must be read from a driver built the same way.
        val driver = JvmDatabaseFactory.createDriver(File(tempDir(), "ejournal.db"))
        val busyTimeout = driver.pragma("busy_timeout")
        driver.close()

        assertEquals("30000", busyTimeout)
    }

    @Test
    fun `concurrent upsertBars across symbols do not raise SQLITE_BUSY`() {
        val dir = tempDir()
        val repo = SqlDelightMarketDataRepository(JvmDatabaseFactory.create(dir))

        val symbols = (1..40).map { "SYM$it" }
        val barsPerSymbol = 200

        fun barsFor(symbol: String) = (0 until barsPerSymbol).map { i ->
            Bar(
                symbol = symbol,
                timeframe = Timeframe.ONE_MINUTE,
                timestamp = LocalDateTime.parse("2020-01-01T%02d:%02d".format(i / 60, i % 60)),
                open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 100L,
            )
        }

        // Hammer the write path from many real threads at once — the production sync shape.
        runBlocking {
            symbols.map { symbol ->
                async(Dispatchers.IO) { repo.upsertBars(Market.US_STOCKS, barsFor(symbol)) }
            }.awaitAll()

            for (symbol in symbols) {
                val stored = repo.getBars(
                    symbol, Timeframe.ONE_MINUTE, Market.US_STOCKS,
                    from = LocalDateTime.parse("2020-01-01T00:00"),
                    to = LocalDateTime.parse("2020-01-01T23:59"),
                )
                assertEquals(barsPerSymbol, stored.size, "expected all bars persisted for $symbol")
            }
        }
    }
}
