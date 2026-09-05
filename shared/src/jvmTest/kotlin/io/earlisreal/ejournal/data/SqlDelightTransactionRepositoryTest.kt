package io.earlisreal.ejournal.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.BrokerAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTransactionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var txRepo: SqlDelightTransactionRepository
    private lateinit var portfolioRepo: SqlDelightPortfolioRepository

    private fun buildDb(driver: SqlDriver): AppDatabase {
        AppDatabase.Schema.create(driver)
        return AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter, brokerAdapter = BrokerAdapter),
            OhlcvBarAdapter = io.earlisreal.ejournal.OhlcvBar.Adapter(
                marketAdapter = MarketAdapter,
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
    }

    @BeforeTest
    fun setup() {
        db = buildDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        portfolioRepo = SqlDelightPortfolioRepository(db)
        txRepo = SqlDelightTransactionRepository(db)
    }

    private suspend fun seedPortfolio(): Long = portfolioRepo.insert("Moomoo Day Trading", Market.US_STOCKS).id

    private fun tx(
        portfolioId: Long,
        action: Action = Action.BUY,
        symbol: String = "BDO",
        datetime: String = "2024-01-01T09:30",
        price: Double = 100.0,
        shares: Double = 100.0,
        fees: Double = 20.0,
        externalId: String? = null,
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
    fun sameExternalIdMayBeUsedByDifferentPortfolios() = runTest {
        val firstPortfolio = seedPortfolio()
        val secondPortfolio = portfolioRepo.insert("Other", Market.US_STOCKS).id

        assertNotNull(txRepo.insert(tx(firstPortfolio, externalId = "same")))
        assertNotNull(txRepo.insert(tx(secondPortfolio, externalId = "same")))

        assertEquals(1, txRepo.getByPortfolio(firstPortfolio).size)
        assertEquals(1, txRepo.getByPortfolio(secondPortfolio).size)
    }

    @Test
    fun insertReturnsRowIdThenNullWhenDuplicateSkipped() = runTest {
        val pId = seedPortfolio()
        val firstId = txRepo.insert(tx(pId, externalId = "tz:1"))
        assertNotNull(firstId)
        assertNull(txRepo.insert(tx(pId, externalId = "tz:1")))
    }

    @Test
    fun insertReturnsRowIdForNewRowOnFileBackedDriver() = runTest {
        // The real DB is file-backed. JdbcSqliteDriver pools connections for file URLs (unlike the
        // single connection it keeps for :memory:), so changes()/last_insert_rowid() can run on a
        // different connection than the INSERT and report a freshly-inserted row as "not inserted".
        val dbFile = File.createTempFile("ejournal-insert-test", ".db").apply { delete(); deleteOnExit() }
        val db = buildDb(JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}"))
        val fileTxRepo = SqlDelightTransactionRepository(db)
        val filerPortfolioRepo = SqlDelightPortfolioRepository(db)
        val pId = filerPortfolioRepo.insert("File Portfolio", Market.US_STOCKS).id

        val firstId = fileTxRepo.insert(tx(pId, externalId = "tz:1"))
        assertNotNull(firstId)                                       // new row must report its id, not null
        assertNull(fileTxRepo.insert(tx(pId, externalId = "tz:1")))  // duplicate still skipped
        assertEquals(1, fileTxRepo.getByPortfolio(pId).size)
    }

    @Test
    fun feeReplacementIsScopedAndAtomic() = runTest {
        val first = seedPortfolio()
        val second = portfolioRepo.insert("Other", Market.US_STOCKS).id
        txRepo.insert(tx(first, fees = 2.0, externalId = "alpaca:first"))
        txRepo.insert(tx(first, fees = 3.0, externalId = "alpaca:second"))
        txRepo.insert(tx(second, fees = 4.0, externalId = "alpaca:other"))

        txRepo.replaceFeesByExternalId(first, mapOf("alpaca:first" to 0.25, "alpaca:second" to 0.75))

        assertEquals(listOf(0.25, 0.75), txRepo.getByPortfolio(first).map { it.fees })
        assertEquals(4.0, txRepo.getByPortfolio(second).single().fees)

        assertFailsWith<IllegalArgumentException> {
            txRepo.replaceFeesByExternalId(
                first,
                linkedMapOf("alpaca:first" to 9.0, "alpaca:second" to Double.NaN),
            )
        }
        assertEquals(listOf(0.25, 0.75), txRepo.getByPortfolio(first).map { it.fees })
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
        val p2 = portfolioRepo.insert("Moomoo", Market.US_STOCKS).id
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
        val p2 = portfolioRepo.insert("Other", Market.US_STOCKS).id
        txRepo.insert(tx(p1)); txRepo.insert(tx(p1)); txRepo.insert(tx(p2))
        assertEquals(2L, txRepo.countByPortfolio(p1))
        assertEquals(1L, txRepo.countByPortfolio(p2))
    }

    @Test
    fun deleteByPortfolioRemovesOnlyThatPortfoliosTransactions() = runTest {
        val p1 = seedPortfolio()
        val p2 = portfolioRepo.insert("Other", Market.US_STOCKS).id
        txRepo.insert(tx(p1, symbol = "BDO"))
        txRepo.insert(tx(p2, symbol = "TSLA"))
        txRepo.deleteByPortfolio(p1)
        assertEquals(0, txRepo.getByPortfolio(p1).size)
        assertEquals(1, txRepo.getByPortfolio(p2).size)
    }

    @Test
    fun deletingTransactionRemovesItsTagAssignments() = runTest {
        val pId = seedPortfolio()
        val txId = txRepo.insert(tx(pId))!!
        val tagRepo = SqlDelightTagRepository(db)
        val tagId = tagRepo.create(pId, "Breakout", "#4CAF50")
        tagRepo.addTag(portfolioId = pId, openingTxId = txId, tagId = tagId)

        txRepo.delete(txId)

        assertTrue(tagRepo.getTagsForOpeningTxIds(pId, listOf(txId)).isEmpty())
    }

    @Test
    fun deletingPortfolioRemovesItsTagAssignments() = runTest {
        val pId = seedPortfolio()
        val txId = txRepo.insert(tx(pId))!!
        val tagRepo = SqlDelightTagRepository(db)
        val tagId = tagRepo.create(pId, "Breakout", "#4CAF50")
        tagRepo.addTag(portfolioId = pId, openingTxId = txId, tagId = tagId)

        txRepo.deleteByPortfolio(pId)

        assertTrue(tagRepo.getTagsForOpeningTxIds(pId, listOf(txId)).isEmpty())
    }
}
