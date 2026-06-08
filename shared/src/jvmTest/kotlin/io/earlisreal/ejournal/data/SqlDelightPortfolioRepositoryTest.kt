package io.earlisreal.ejournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
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
            )
        )
        repo = SqlDelightPortfolioRepository(db)
    }

    @Test
    fun insertAndRetrievePortfolio() = runTest {
        val id = repo.insert("COL Financial", "PHP")
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("COL Financial", all[0].name)
        assertEquals("PHP", all[0].currency)
        assertEquals(id, all[0].id)
    }

    @Test
    fun getByIdReturnsCorrectPortfolio() = runTest {
        val id = repo.insert("COL Financial", "PHP")
        repo.insert("Moomoo Day Trading", "USD")
        val found = repo.getById(id)
        assertEquals("COL Financial", found?.name)
    }

    @Test
    fun getByIdReturnsNullForMissingId() = runTest {
        assertNull(repo.getById(999L))
    }

    @Test
    fun deleteRemovesPortfolio() = runTest {
        val id = repo.insert("Moomoo", "USD")
        repo.delete(id)
        assertTrue(repo.getAll().isEmpty())
    }
}
