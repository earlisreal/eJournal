package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.BrokerAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightPortfolioRepositoryTest {

    private lateinit var repo: SqlDelightPortfolioRepository
    private lateinit var tagRepo: SqlDelightTagRepository
    private lateinit var transactionRepo: SqlDelightTransactionRepository

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val db = AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                verifiedExecutionInstantAdapter = io.earlisreal.ejournal.data.database.InstantAdapter,
                verifiedMarketDateAdapter = io.earlisreal.ejournal.data.database.LocalDateAdapter,
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter, brokerAdapter = BrokerAdapter),
            OhlcvBarAdapter = io.earlisreal.ejournal.OhlcvBar.Adapter(
                marketAdapter = MarketAdapter,
                timestampAdapter = DateTimeAdapter,
                timeframeAdapter = io.earlisreal.ejournal.data.database.TimeframeAdapter,
            ),
        )
        repo = SqlDelightPortfolioRepository(db)
        tagRepo = SqlDelightTagRepository(db)
        transactionRepo = SqlDelightTransactionRepository(db)
    }

    @Test
    fun marketEditsClearDateEvidenceButRetainAbsoluteInstants() = runTest {
        val p = repo.insert("First", Market.US_STOCKS)
        val instant = kotlin.time.Instant.parse("2026-01-02T14:30:00Z")
        val input = Transaction(0, p.id, "X", LocalDateTime(2026, 1, 2, 9, 30), Action.BUY, 10.0, 1.0, 0.2,
            verifiedExecutionInstant = instant, verifiedMarketDate = kotlinx.datetime.LocalDate(2026, 1, 2))
        transactionRepo.insertOrEnrich(input)
        repo.update(p.id, "Renamed", Market.US_STOCKS)
        assertEquals(input.verifiedMarketDate, transactionRepo.getByPortfolio(p.id).single().verifiedMarketDate)
        repo.update(p.id, "Renamed", Market.PH_STOCKS)
        val saved = transactionRepo.getByPortfolio(p.id).single()
        assertNull(saved.verifiedMarketDate)
        assertEquals(instant, saved.verifiedExecutionInstant)
        assertEquals(input.datetime, saved.datetime)
    }

    @Test
    fun insertAndRetrievePortfolioRoundTripsMarket() = runTest {
        val portfolio = repo.insert("COL Financial", Market.PH_STOCKS)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("COL Financial", all[0].name)
        assertEquals(Market.PH_STOCKS, all[0].market)
        assertEquals(portfolio.id, all[0].id)
        assertTrue(portfolio.credentialRef.isNotBlank())
    }

    @Test
    fun eachPortfolioGetsAUniqueCredentialReference() = runTest {
        val first = repo.insert("First", Market.US_STOCKS)
        val second = repo.insert("Second", Market.US_STOCKS)

        assertTrue(first.credentialRef.isNotBlank())
        assertTrue(second.credentialRef.isNotBlank())
        kotlin.test.assertNotEquals(first.credentialRef, second.credentialRef)
    }

    @Test
    fun updateChangesNameAndMarket() = runTest {
        val portfolio = repo.insert("COL", Market.PH_STOCKS, Broker.ALPACA)
        repo.update(portfolio.id, "COL Financial", Market.US_STOCKS, Broker.TRADEZERO)
        val found = repo.getById(portfolio.id)!!
        assertEquals("COL Financial", found.name)
        assertEquals(Market.US_STOCKS, found.market)
        assertEquals(Broker.TRADEZERO, found.broker)
        assertEquals(portfolio.credentialRef, found.credentialRef)
    }

    @Test
    fun alpacaEnvironmentRoundTripsIndependentlyOfCredentials() = runTest {
        val portfolio = repo.insert("Alpaca", Market.US_STOCKS, Broker.ALPACA, AlpacaEnvironment.LIVE)

        assertEquals(AlpacaEnvironment.LIVE, repo.getById(portfolio.id)!!.alpacaEnvironment)

        repo.update(portfolio.id, "Alpaca", Market.US_STOCKS, Broker.ALPACA, AlpacaEnvironment.PAPER)

        assertEquals(AlpacaEnvironment.PAPER, repo.getById(portfolio.id)!!.alpacaEnvironment)
    }

    @Test
    fun getByIdReturnsNullForMissingId() = runTest {
        assertNull(repo.getById(999L))
    }

    @Test
    fun deleteRemovesPortfolio() = runTest {
        val id = repo.insert("Moomoo", Market.CRYPTO).id
        repo.delete(id)
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun deleteRemovesPortfolioTagsAndAssignmentsOnlyForThatPortfolio() = runTest {
        val first = repo.insert("First", Market.US_STOCKS)
        val second = repo.insert("Second", Market.US_STOCKS)
        val firstTx = transactionRepo.insert(transaction(first.id))!!
        val secondTx = transactionRepo.insert(transaction(second.id))!!
        val firstTag = tagRepo.create(first.id, "First tag", "#111111")
        val secondTag = tagRepo.create(second.id, "Second tag", "#222222")
        tagRepo.addTag(first.id, firstTx, firstTag)
        tagRepo.addTag(second.id, secondTx, secondTag)

        repo.delete(first.id)

        assertTrue(tagRepo.getAll(first.id).isEmpty())
        assertTrue(tagRepo.getTagsForOpeningTxIds(first.id, listOf(firstTx)).isEmpty())
        assertEquals(listOf(secondTag), tagRepo.getAll(second.id).map { it.id })
        assertEquals(listOf(secondTag), tagRepo.getTagsForOpeningTxIds(second.id, listOf(secondTx)).getValue(secondTx).map { it.id })
    }

    private fun transaction(portfolioId: Long) = Transaction(
        id = 0L,
        portfolioId = portfolioId,
        symbol = "AAPL",
        datetime = LocalDateTime(2026, 6, 1, 9, 30),
        action = Action.BUY,
        price = 100.0,
        shares = 10.0,
        fees = 1.0,
    )
}
