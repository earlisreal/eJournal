package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.OhlcvBar
import io.earlisreal.ejournal.Portfolio
import io.earlisreal.ejournal.TradeTransaction
import java.io.File

object JvmDatabaseFactory {

    fun create(dbDir: File = File(System.getProperty("user.home"), ".ejournal")): AppDatabase {
        dbDir.mkdirs()
        val dbFile = File(dbDir, "ejournal.db")
        val isNew = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
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
                datetimeAdapter = DateTimeAdapter,
                actionAdapter   = ActionAdapter
            ),
            PortfolioAdapter = Portfolio.Adapter(marketAdapter = MarketAdapter),
            OhlcvBarAdapter = OhlcvBar.Adapter(
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
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
