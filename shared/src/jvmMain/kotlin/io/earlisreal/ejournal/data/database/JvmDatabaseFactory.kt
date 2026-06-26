package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.OhlcvBar
import io.earlisreal.ejournal.Portfolio
import io.earlisreal.ejournal.TradeTransaction
import org.sqlite.SQLiteConfig
import java.io.File

object JvmDatabaseFactory {

    // Market-data sync fires many upsertBars write transactions concurrently (the provider
    // semaphores gate only the network fetch), and JdbcSqliteDriver pools connections for file
    // URLs. In rollback-journal mode SQLite's deadlock-avoidance returns SQLITE_BUSY immediately
    // on such concurrent writers, ignoring busy_timeout. WAL serializes writers via the write
    // lock and honors busy_timeout, so contending writers wait instead of crashing.
    private const val BUSY_TIMEOUT_MS = 30_000

    fun create(dbDir: File = File(System.getProperty("user.home"), ".ejournal")): AppDatabase {
        dbDir.mkdirs()
        val dbFile = File(dbDir, "ejournal.db")
        val isNew = !dbFile.exists()
        val driver = createDriver(dbFile)
        val targetVersion = AppDatabase.Schema.version

        if (isNew) {
            AppDatabase.Schema.create(driver)
            setVersion(driver, targetVersion)
        } else {
            // Pre-migration installs never stamped user_version, so 0 means "version 1".
            val current = currentVersion(driver).coerceAtLeast(1)
            if (current < targetVersion) {
                AppDatabase.Schema.migrate(driver, current, targetVersion)
                setVersion(driver, targetVersion)
            }
        }

        return AppDatabase(
            driver = driver,
            TradeTransactionAdapter = TradeTransaction.Adapter(
                datetimeAdapter  = DateTimeAdapter,
                actionAdapter    = ActionAdapter
            ),
            PortfolioAdapter = Portfolio.Adapter(marketAdapter = MarketAdapter),
            OhlcvBarAdapter = OhlcvBar.Adapter(
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
    }

    /** Opens the SQLite file in WAL mode with a generous busy timeout so concurrent sync writes queue rather than fail. */
    internal fun createDriver(dbFile: File): JdbcSqliteDriver {
        val config = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            busyTimeout = BUSY_TIMEOUT_MS
        }
        return JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", config.toProperties())
    }

    private fun currentVersion(driver: SqlDriver): Long =
        driver.executeQuery(
            null,
            "PRAGMA user_version",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0,
        ).value

    private fun setVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}
