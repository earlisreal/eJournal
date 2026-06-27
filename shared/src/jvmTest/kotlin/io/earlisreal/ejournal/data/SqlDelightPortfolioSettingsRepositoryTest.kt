package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightPortfolioSettingsRepositoryTest {

    private lateinit var repo: SqlDelightPortfolioSettingsRepository

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val db = AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter,
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter),
            OhlcvBarAdapter = io.earlisreal.ejournal.OhlcvBar.Adapter(
                marketAdapter = MarketAdapter,
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
        repo = SqlDelightPortfolioSettingsRepository(db)
    }

    @Test
    fun getStringReturnsNullWhenUnset() = runTest {
        assertNull(repo.getString(1L, "k"))
    }

    @Test
    fun putStringRoundTripsAndOverwrites() = runTest {
        repo.putString(1L, "k", "v1")
        assertEquals("v1", repo.getString(1L, "k"))
        repo.putString(1L, "k", "v2")
        assertEquals("v2", repo.getString(1L, "k"))
    }

    @Test
    fun getBooleanReturnsDefaultWhenUnsetOrCorrupt() = runTest {
        assertTrue(repo.getBoolean(1L, "flag", default = true))
        assertFalse(repo.getBoolean(1L, "flag", default = false))
        repo.putString(1L, "flag", "garbage")
        assertFalse(repo.getBoolean(1L, "flag", default = false))
    }

    @Test
    fun putBooleanRoundTrips() = runTest {
        repo.putBoolean(1L, "flag", true)
        assertTrue(repo.getBoolean(1L, "flag", default = false))
        repo.putBoolean(1L, "flag", false)
        assertFalse(repo.getBoolean(1L, "flag", default = true))
    }

    @Test
    fun settingsAreIsolatedPerPortfolio() = runTest {
        repo.putString(1L, "k", "one")
        repo.putString(2L, "k", "two")
        assertEquals("one", repo.getString(1L, "k"))
        assertEquals("two", repo.getString(2L, "k"))
    }

    @Test
    fun clearRemovesOnlyThatPortfoliosSettings() = runTest {
        repo.putString(1L, "a", "x")
        repo.putString(1L, "b", "y")
        repo.putString(2L, "a", "z")
        repo.clear(1L)
        assertNull(repo.getString(1L, "a"))
        assertNull(repo.getString(1L, "b"))
        assertEquals("z", repo.getString(2L, "a"))
    }
}
