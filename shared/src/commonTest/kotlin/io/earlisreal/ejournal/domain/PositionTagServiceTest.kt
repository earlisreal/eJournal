package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionTagServiceTest {

    private val breakout = Tag(1, 1, "Breakout", "#4CAF50")

    private fun txn(id: Long) = Transaction(
        id = id, portfolioId = 1, symbol = "AAPL",
        datetime = LocalDateTime(2026, 6, 1, 9, 30), action = Action.BUY,
        price = 100.0, shares = 10.0, fees = 1.0,
    )

    private fun positionOpenedBy(txId: Long, symbol: String = "AAPL") = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime(2026, 6, 1, 9, 30),
        exitDatetime = LocalDateTime(2026, 6, 1, 10, 30),
        averageEntryPrice = 100.0, averageExitPrice = 110.0,
        shares = 10.0, fees = 1.0, profitLoss = 90.0,
        transactions = listOf(txn(txId)),
    )

    private class StubTx(val txs: List<Transaction>) : TransactionRepository {
        override suspend fun getByPortfolio(portfolioId: Long) = txs
        override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime) = emptyList<Transaction>()
        override suspend fun insert(transaction: Transaction): Long? = null
        override suspend fun delete(id: Long) {}
        override suspend fun countByPortfolio(portfolioId: Long) = txs.size.toLong()
        override suspend fun deleteByPortfolio(portfolioId: Long) {}
    }

    private class StubPortfolio : PortfolioRepository {
        override suspend fun getAll() = emptyList<Portfolio>()
        override suspend fun getById(id: Long): Portfolio? = null
        override suspend fun insert(name: String, market: Market, broker: io.earlisreal.ejournal.domain.model.Broker?, alpacaEnvironment: io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment?) =
            Portfolio(0, name, market, broker, "stub")
        override suspend fun update(id: Long, name: String, market: Market, broker: io.earlisreal.ejournal.domain.model.Broker?, alpacaEnvironment: io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment?) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakeTagRepo : TagRepository {
        val assignments = mutableMapOf<Long, MutableList<Tag>>()
        val hydratedPortfolioIds = mutableListOf<Long>()
        val added = mutableListOf<Pair<Long, Long>>()
        val removed = mutableListOf<Pair<Long, Long>>()
        override suspend fun getAll(portfolioId: Long) = emptyList<Tag>()
        override suspend fun create(portfolioId: Long, name: String, color: String) = 0L
        override suspend fun update(portfolioId: Long, id: Long, name: String, color: String) {}
        override suspend fun delete(portfolioId: Long, id: Long) {}
        override suspend fun getTagsForOpeningTxIds(portfolioId: Long, openingTxIds: List<Long>): Map<Long, List<Tag>> {
            hydratedPortfolioIds += portfolioId
            return openingTxIds.mapNotNull { id -> assignments[id]?.let { id to it.toList() } }.toMap()
        }
        override suspend fun addTag(portfolioId: Long, openingTxId: Long, tagId: Long) {
            require(portfolioId == 1L)
            added += openingTxId to tagId
        }
        override suspend fun removeTag(portfolioId: Long, openingTxId: Long, tagId: Long) {
            require(portfolioId == 1L)
            removed += openingTxId to tagId
        }
    }

    @Test
    fun attachesTagsToPositionsByOpeningTransactionId() = runTest {
        val positions = listOf(positionOpenedBy(100L), positionOpenedBy(200L, "TSLA"))
        val closed = ClosedPositionService(StubTx(listOf(txn(100L))), StubPortfolio()) { positions }
        val tagRepo = FakeTagRepo().apply { assignments[100L] = mutableListOf(breakout) }
        val service = PositionTagService(closed, tagRepo)

        val result = service.forPortfolio(1L)
        assertEquals(listOf(1L), tagRepo.hydratedPortfolioIds)
        assertEquals(listOf(breakout), result.first { it.symbol == "AAPL" }.tags)
        assertEquals(emptyList<Tag>(), result.first { it.symbol == "TSLA" }.tags)
    }

    @Test
    fun addAndRemoveResolveThePositionsOpeningTransactionId() = runTest {
        val closed = ClosedPositionService(StubTx(emptyList()), StubPortfolio()) { emptyList() }
        val tagRepo = FakeTagRepo()
        val service = PositionTagService(closed, tagRepo)
        val pos = positionOpenedBy(555L)

        service.addTag(1L, pos, breakout.id)
        service.removeTag(1L, pos, breakout.id)

        assertEquals(listOf(555L to breakout.id), tagRepo.added)
        assertEquals(listOf(555L to breakout.id), tagRepo.removed)
    }

    @Test
    fun crossPortfolioAssignmentIsRejectedByTheRepositoryBoundary() = runTest {
        val closed = ClosedPositionService(StubTx(emptyList()), StubPortfolio()) { emptyList() }
        val tagRepo = FakeTagRepo()
        val service = PositionTagService(closed, tagRepo)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            service.addTag(2L, positionOpenedBy(555L), breakout.id)
        }
    }
}
