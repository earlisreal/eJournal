package io.earlisreal.ejournal.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.data.database.ActionAdapter
import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.database.DateTimeAdapter
import io.earlisreal.ejournal.data.database.MarketAdapter
import io.earlisreal.ejournal.data.database.TimeframeAdapter
import io.earlisreal.ejournal.domain.model.Tag
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SqlDelightTagRepositoryTest {

    private lateinit var tagRepo: SqlDelightTagRepository

    private fun buildDb(driver: SqlDriver): AppDatabase {
        AppDatabase.Schema.create(driver)
        return AppDatabase(
            driver = driver,
            TradeTransactionAdapter = io.earlisreal.ejournal.TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter = ActionAdapter
            ),
            PortfolioAdapter = io.earlisreal.ejournal.Portfolio.Adapter(marketAdapter = MarketAdapter),
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
    }

    @Test
    fun createAndGetAll() = runTest {
        val id = tagRepo.create("Breakout", "#4CAF50")
        assertEquals(listOf(Tag(id, "Breakout", "#4CAF50")), tagRepo.getAll())
    }

    @Test
    fun updateChangesNameAndColor() = runTest {
        val id = tagRepo.create("Breakout", "#4CAF50")
        tagRepo.update(id, "Breakouts", "#000000")
        assertEquals(Tag(id, "Breakouts", "#000000"), tagRepo.getAll().single())
    }

    @Test
    fun deleteRemovesTag() = runTest {
        val id = tagRepo.create("X", "#111111")
        tagRepo.delete(id)
        assertTrue(tagRepo.getAll().isEmpty())
    }

    @Test
    fun tagNamesAreCaseInsensitivelyUnique() = runTest {
        tagRepo.create("Breakout", "#111111")
        assertFails { tagRepo.create("breakout", "#222222") }
    }

    @Test
    fun assignAndFetchTagsForPositions() = runTest {
        val t1 = tagRepo.create("A", "#111111")
        val t2 = tagRepo.create("B", "#222222")
        tagRepo.addTag(openingTxId = 100L, tagId = t1)
        tagRepo.addTag(openingTxId = 100L, tagId = t2)
        tagRepo.addTag(openingTxId = 200L, tagId = t1)

        val map = tagRepo.getTagsForOpeningTxIds(listOf(100L, 200L))
        assertEquals(setOf(t1, t2), map.getValue(100L).map { it.id }.toSet())
        assertEquals(listOf(t1), map.getValue(200L).map { it.id })
    }

    @Test
    fun addTagIsIdempotent() = runTest {
        val t = tagRepo.create("A", "#111111")
        tagRepo.addTag(1L, t)
        tagRepo.addTag(1L, t)
        assertEquals(1, tagRepo.getTagsForOpeningTxIds(listOf(1L)).getValue(1L).size)
    }

    @Test
    fun removeTagAssignment() = runTest {
        val t = tagRepo.create("A", "#111111")
        tagRepo.addTag(1L, t)
        tagRepo.removeTag(1L, t)
        assertTrue(tagRepo.getTagsForOpeningTxIds(listOf(1L)).isEmpty())
    }

    @Test
    fun deletingTagRemovesItsAssignments() = runTest {
        val t = tagRepo.create("A", "#111111")
        tagRepo.addTag(1L, t)
        tagRepo.delete(t)
        assertTrue(tagRepo.getTagsForOpeningTxIds(listOf(1L)).isEmpty())
    }

    @Test
    fun getTagsForEmptyListReturnsEmpty() = runTest {
        assertEquals(emptyMap(), tagRepo.getTagsForOpeningTxIds(emptyList()))
    }
}
