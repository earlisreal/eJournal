package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqlDelightTransactionRepositoryTest {

    private lateinit var txRepo: SqlDelightTransactionRepository
    private lateinit var portfolioRepo: SqlDelightPortfolioRepository

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
        portfolioRepo = SqlDelightPortfolioRepository(db)
        txRepo = SqlDelightTransactionRepository(db)
    }

    private suspend fun seedPortfolio(): Long = portfolioRepo.insert("Moomoo Day Trading", Market.US_STOCKS)

    private fun tx(
        portfolioId: Long,
        action: Action = Action.BUY,
        symbol: String = "BDO",
        datetime: String = "2024-01-01T09:30",
        price: Double = 100.0,
        shares: Double = 100.0,
        fees: Double = 20.0,
        externalId: String? = null
    ) = Transaction(
        id = 0L,
        portfolioId = portfolioId,
        symbol = symbol,
        datetime = LocalDateTime.parse(datetime),
        action = action,
        price = price,
        shares = shares,
        fees = fees,
        externalId = externalId
    )

    @Test
    fun insertAndRetrieveTransaction() = runTest {
        val pId = seedPortfolio()
        txRepo.insert(tx(pId))
        val result = txRepo.getByPortfolio(pId)
        assertEquals(1, result.size)
        assertEquals("BDO", result[0].symbol)
        assertEquals(Action.BUY, result[0].action)
        assertEquals(100.0, result[0].price)
    }

    @Test
    fun duplicateExternalIdIsNotInserted() = runTest {
        val pId = seedPortfolio()
        txRepo.insert(tx(pId, externalId = "tz:1"))
        txRepo.insert(tx(pId, externalId = "tz:1"))
        assertEquals(1, txRepo.getByPortfolio(pId).size)
    }

    @Test
    fun insertReturnsRowIdThenNullWhenDuplicateSkipped() = runTest {
        val pId = seedPortfolio()
        val firstId = txRepo.insert(tx(pId, externalId = "tz:1"))
        assertNotNull(firstId)
        assertNull(txRepo.insert(tx(pId, externalId = "tz:1")))
    }

    @Test
    fun rowsWithNullExternalIdAreAlwaysInserted() = runTest {
        val pId = seedPortfolio()
        txRepo.insert(tx(pId, externalId = null))
        txRepo.insert(tx(pId, externalId = null))
        assertEquals(2, txRepo.getByPortfolio(pId).size)
    }

    @Test
    fun getByPortfolioReturnsOnlyMatchingPortfolio() = runTest {
        val p1 = seedPortfolio()
        val p2 = portfolioRepo.insert("Moomoo", Market.US_STOCKS)
        txRepo.insert(tx(p1, symbol = "BDO"))
        txRepo.insert(tx(p2, symbol = "TSLA"))
        assertEquals(1, txRepo.getByPortfolio(p1).size)
        assertEquals("BDO", txRepo.getByPortfolio(p1)[0].symbol)
    }

    @Test
    fun getByDateRangeFiltersCorrectly() = runTest {
        val pId = seedPortfolio()
        txRepo.insert(tx(pId, datetime = "2024-01-01T09:30"))
        txRepo.insert(tx(pId, datetime = "2024-06-01T09:30"))
        txRepo.insert(tx(pId, datetime = "2024-12-01T09:30"))

        val from = LocalDateTime.parse("2024-01-01T00:00")
        val to   = LocalDateTime.parse("2024-06-30T23:59")
        val result = txRepo.getByPortfolioAndDateRange(pId, from, to)
        assertEquals(2, result.size)
    }

    @Test
    fun deleteRemovesTransaction() = runTest {
        val pId = seedPortfolio()
        val id = txRepo.insert(tx(pId))!!
        txRepo.delete(id)
        assertEquals(0, txRepo.getByPortfolio(pId).size)
    }

    @Test
    fun countByPortfolioCountsOnlyThatPortfolio() = runTest {
        val p1 = seedPortfolio()
        val p2 = portfolioRepo.insert("Other", Market.US_STOCKS)
        txRepo.insert(tx(p1)); txRepo.insert(tx(p1)); txRepo.insert(tx(p2))
        assertEquals(2L, txRepo.countByPortfolio(p1))
        assertEquals(1L, txRepo.countByPortfolio(p2))
    }

    @Test
    fun deleteByPortfolioRemovesOnlyThatPortfoliosTransactions() = runTest {
        val p1 = seedPortfolio()
        val p2 = portfolioRepo.insert("Other", Market.US_STOCKS)
        txRepo.insert(tx(p1, symbol = "BDO"))
        txRepo.insert(tx(p2, symbol = "TSLA"))
        txRepo.deleteByPortfolio(p1)
        assertEquals(0, txRepo.getByPortfolio(p1).size)
        assertEquals(1, txRepo.getByPortfolio(p2).size)
    }
}
