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
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightPositionNoteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var noteRepo: SqlDelightPositionNoteRepository

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
        db = buildDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        noteRepo = SqlDelightPositionNoteRepository(db)
    }

    @Test
    fun storesAndFetchesNotesByOpeningTransactionId() = runTest {
        noteRepo.setNote(100L, "Entry reason\nFollowed the plan")
        noteRepo.setNote(200L, "Waited for confirmation")

        assertEquals(
            mapOf(
                100L to "Entry reason\nFollowed the plan",
                200L to "Waited for confirmation",
            ),
            noteRepo.getNotesForOpeningTxIds(listOf(100L, 200L)),
        )
    }

    @Test
    fun blankNotesRemoveStoredValue() = runTest {
        noteRepo.setNote(100L, "Keep this")
        noteRepo.setNote(100L, "  \n\t")

        assertEquals(emptyMap(), noteRepo.getNotesForOpeningTxIds(listOf(100L)))
    }

    @Test
    fun emptyOpeningIdsReturnEmpty() = runTest {
        assertEquals(emptyMap(), noteRepo.getNotesForOpeningTxIds(emptyList()))
    }

    @Test
    fun deletingOpeningTransactionDeletesItsNote() = runTest {
        val transactionRepo = SqlDelightTransactionRepository(db)
        val openingTxId = transactionRepo.insert(transaction())!!
        noteRepo.setNote(openingTxId, "Remove me")

        transactionRepo.delete(openingTxId)

        assertEquals(emptyMap(), noteRepo.getNotesForOpeningTxIds(listOf(openingTxId)))
    }

    @Test
    fun deletingPortfolioDeletesItsNotes() = runTest {
        val transactionRepo = SqlDelightTransactionRepository(db)
        val openingTxId = transactionRepo.insert(transaction(portfolioId = 7L))!!
        noteRepo.setNote(openingTxId, "Remove with portfolio")

        transactionRepo.deleteByPortfolio(7L)

        assertEquals(emptyMap(), noteRepo.getNotesForOpeningTxIds(listOf(openingTxId)))
    }

    private fun transaction(portfolioId: Long = 1L) = Transaction(
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
