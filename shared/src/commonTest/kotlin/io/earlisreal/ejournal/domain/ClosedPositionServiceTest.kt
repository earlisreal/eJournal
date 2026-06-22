// shared/src/commonTest/kotlin/io/earlisreal/ejournal/domain/ClosedPositionServiceTest.kt
package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

private class StubTransactionRepository(var txs: List<Transaction>) : TransactionRepository {
    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> = txs
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime) = emptyList<Transaction>()
    override suspend fun insert(transaction: Transaction): Long? = null
    override suspend fun delete(id: Long) {}
    override suspend fun countByPortfolio(portfolioId: Long): Long = txs.size.toLong()
    override suspend fun deleteByPortfolio(portfolioId: Long) {}
}

class ClosedPositionServiceTest {

    private fun txn(id: Long) = Transaction(
        id = id, portfolioId = 1, symbol = "AAPL",
        datetime = LocalDateTime(2026, 6, 1, 9, 30), action = Action.BUY,
        price = 100.0, shares = 10.0, fees = 1.0,
    )

    @Test
    fun computesOncePerUnchangedPortfolio() = runTest {
        var computeCalls = 0
        val service = ClosedPositionService(StubTransactionRepository(listOf(txn(1)))) { computeCalls++; emptyList() }
        service.forPortfolio(1L)
        service.forPortfolio(1L)
        assertEquals(1, computeCalls)
    }

    @Test
    fun recomputesWhenTransactionsChange() = runTest {
        var computeCalls = 0
        val repo = StubTransactionRepository(listOf(txn(1)))
        val service = ClosedPositionService(repo) { computeCalls++; emptyList() }
        service.forPortfolio(1L)
        repo.txs = listOf(txn(1), txn(2)) // signature changes
        service.forPortfolio(1L)
        assertEquals(2, computeCalls)
    }

    @Test
    fun cachesIndependentlyPerPortfolio() = runTest {
        var computeCalls = 0
        val service = ClosedPositionService(StubTransactionRepository(listOf(txn(1)))) { computeCalls++; emptyList() }
        service.forPortfolio(1L)
        service.forPortfolio(2L)
        assertEquals(2, computeCalls)
    }
}
