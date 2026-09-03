package io.earlisreal.ejournal.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.BrokerAdapter
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightTagRepositoryTest {

    private lateinit var tagRepo: SqlDelightTagRepository
    private lateinit var portfolioRepo: SqlDelightPortfolioRepository
    private lateinit var transactionRepo: SqlDelightTransactionRepository

    private fun buildDb(driver: SqlDriver): AppDatabase {
        AppDatabase.Schema.create(driver)
        return AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter,
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(
                marketAdapter = MarketAdapter,
                brokerAdapter = BrokerAdapter,
            ),
            OhlcvBarAdapter = io.earlisreal.ejournal.OhlcvBar.Adapter(
                marketAdapter = MarketAdapter,
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = TimeframeAdapter,
            ),
        )
    }

    @BeforeTest
    fun setup() {
        val db = buildDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        tagRepo = SqlDelightTagRepository(db)
        portfolioRepo = SqlDelightPortfolioRepository(db)
        transactionRepo = SqlDelightTransactionRepository(db)
    }

    private suspend fun portfolio(name: String = "Portfolio"): Long =
        portfolioRepo.insert(name, Market.US_STOCKS).id

    private suspend fun transaction(portfolioId: Long, symbol: String = "AAPL"): Long =
        transactionRepo.insert(
            Transaction(
                id = 0L,
                portfolioId = portfolioId,
                symbol = symbol,
                datetime = LocalDateTime(2026, 6, 1, 9, 30),
                action = Action.BUY,
                price = 100.0,
                shares = 10.0,
                fees = 1.0,
            ),
        )!!

    @Test
    fun createAndGetAllIsPortfolioScoped() = runTest {
        val portfolioId = portfolio()
        val id = tagRepo.create(portfolioId, "Breakout", "#4CAF50")

        assertEquals(listOf(Tag(id, portfolioId, "Breakout", "#4CAF50")), tagRepo.getAll(portfolioId))
    }

    @Test
    fun updateChangesNameAndColorWithinPortfolio() = runTest {
        val portfolioId = portfolio()
        val id = tagRepo.create(portfolioId, "Breakout", "#4CAF50")

        tagRepo.update(portfolioId, id, "Breakouts", "#000000")

        assertEquals(Tag(id, portfolioId, "Breakouts", "#000000"), tagRepo.getAll(portfolioId).single())
    }

    @Test
    fun deleteRemovesOnlyTheOwningPortfolioTag() = runTest {
        val first = portfolio("First")
        val second = portfolio("Second")
        val id = tagRepo.create(first, "X", "#111111")

        tagRepo.delete(second, id)

        assertEquals(1, tagRepo.getAll(first).size)
    }

    @Test
    fun tagNamesAreCaseInsensitivelyUniqueWithinPortfolio() = runTest {
        val first = portfolio("First")
        val second = portfolio("Second")
        tagRepo.create(first, "Breakout", "#111111")

        assertFailsWith<Exception> { tagRepo.create(first, "breakout", "#222222") }
        tagRepo.create(second, "breakout", "#222222")
        assertEquals("breakout", tagRepo.getAll(second).single().name)
    }

    @Test
    fun assignAndFetchTagsForPositionsIsPortfolioScoped() = runTest {
        val portfolioId = portfolio()
        val firstTx = transaction(portfolioId)
        val secondTx = transaction(portfolioId, "TSLA")
        val firstTag = tagRepo.create(portfolioId, "A", "#111111")
        val secondTag = tagRepo.create(portfolioId, "B", "#222222")

        tagRepo.addTag(portfolioId, firstTx, firstTag)
        tagRepo.addTag(portfolioId, firstTx, secondTag)
        tagRepo.addTag(portfolioId, secondTx, firstTag)

        val map = tagRepo.getTagsForOpeningTxIds(portfolioId, listOf(firstTx, secondTx))
        assertEquals(setOf(firstTag, secondTag), map.getValue(firstTx).map { it.id }.toSet())
        assertEquals(listOf(firstTag), map.getValue(secondTx).map { it.id })
    }

    @Test
    fun addTagIsIdempotent() = runTest {
        val portfolioId = portfolio()
        val txId = transaction(portfolioId)
        val tagId = tagRepo.create(portfolioId, "A", "#111111")

        tagRepo.addTag(portfolioId, txId, tagId)
        tagRepo.addTag(portfolioId, txId, tagId)

        assertEquals(1, tagRepo.getTagsForOpeningTxIds(portfolioId, listOf(txId)).getValue(txId).size)
    }

    @Test
    fun crossPortfolioAssignmentIsRejectedWithoutWriting() = runTest {
        val first = portfolio("First")
        val second = portfolio("Second")
        val firstTx = transaction(first)
        val secondTx = transaction(second, "TSLA")
        val firstTag = tagRepo.create(first, "First", "#111111")
        val secondTag = tagRepo.create(second, "Second", "#222222")

        assertFailsWith<IllegalStateException> { tagRepo.addTag(first, secondTx, firstTag) }
        assertFailsWith<IllegalStateException> { tagRepo.addTag(first, firstTx, secondTag) }
        tagRepo.addTag(first, firstTx, firstTag)
        assertFailsWith<IllegalStateException> { tagRepo.removeTag(second, firstTx, firstTag) }

        assertEquals(listOf(firstTag), tagRepo.getTagsForOpeningTxIds(first, listOf(firstTx)).getValue(firstTx).map { it.id })
        assertTrue(tagRepo.getTagsForOpeningTxIds(second, listOf(secondTx)).isEmpty())
    }

    @Test
    fun removeTagAssignment() = runTest {
        val portfolioId = portfolio()
        val txId = transaction(portfolioId)
        val tagId = tagRepo.create(portfolioId, "A", "#111111")
        tagRepo.addTag(portfolioId, txId, tagId)

        tagRepo.removeTag(portfolioId, txId, tagId)

        assertTrue(tagRepo.getTagsForOpeningTxIds(portfolioId, listOf(txId)).isEmpty())
        assertEquals(listOf(tagId), tagRepo.getAll(portfolioId).map { it.id })
    }

    @Test
    fun deletingTagRemovesItsAssignments() = runTest {
        val portfolioId = portfolio()
        val txId = transaction(portfolioId)
        val tagId = tagRepo.create(portfolioId, "A", "#111111")
        tagRepo.addTag(portfolioId, txId, tagId)

        tagRepo.delete(portfolioId, tagId)

        assertTrue(tagRepo.getTagsForOpeningTxIds(portfolioId, listOf(txId)).isEmpty())
    }

    @Test
    fun getTagsForEmptyListReturnsEmpty() = runTest {
        val portfolioId = portfolio()

        assertEquals(emptyMap(), tagRepo.getTagsForOpeningTxIds(portfolioId, emptyList()))
    }
}
