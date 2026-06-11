package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightPortfolioRepositoryTest {

    private lateinit var repo: SqlDelightPortfolioRepository

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
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter)
        )
        repo = SqlDelightPortfolioRepository(db)
    }

    @Test
    fun insertAndRetrievePortfolioRoundTripsMarket() = runTest {
        val id = repo.insert("COL Financial", Market.PH_STOCKS)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("COL Financial", all[0].name)
        assertEquals(Market.PH_STOCKS, all[0].market)
        assertEquals(id, all[0].id)
    }

    @Test
    fun updateChangesNameAndMarket() = runTest {
        val id = repo.insert("COL", Market.PH_STOCKS)
        repo.update(id, "COL Financial", Market.US_STOCKS)
        val found = repo.getById(id)!!
        assertEquals("COL Financial", found.name)
        assertEquals(Market.US_STOCKS, found.market)
    }

    @Test
    fun getByIdReturnsNullForMissingId() = runTest {
        assertNull(repo.getById(999L))
    }

    @Test
    fun deleteRemovesPortfolio() = runTest {
        val id = repo.insert("Moomoo", Market.CRYPTO)
        repo.delete(id)
        assertTrue(repo.getAll().isEmpty())
    }
}
