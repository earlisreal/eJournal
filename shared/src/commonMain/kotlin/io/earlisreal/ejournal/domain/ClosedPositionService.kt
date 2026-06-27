package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Computes a portfolio's closed positions, memoizing the FIFO result so repeat reads (screen
 * switches, the all-portfolios startup market-data sync) don't recompute unchanged data. Keyed by
 * portfolioId; invalidated automatically when the transaction set changes, detected by a content
 * signature (the transaction list's hashCode — Transaction is a data class). Purely in-memory:
 * closed positions are still never persisted.
 *
 * Note: each call still loads transactions from the repository to compute the signature; the cache
 * skips the (more expensive) FIFO recomputation, not the DB read.
 */
class ClosedPositionService(
    private val transactionRepository: TransactionRepository,
    private val portfolioRepository: PortfolioRepository,
    private val compute: (List<Transaction>) -> List<ClosedPosition> = FifoMatcher::computeClosedPositions,
) {
    private data class Entry(val signature: Int, val positions: List<ClosedPosition>)

    private val mutex = Mutex()
    // No eviction needed: entry count is bounded by the (small) number of portfolios.
    private val cache = mutableMapOf<Long, Entry>()

    suspend fun forPortfolio(portfolioId: Long): List<ClosedPosition> {
        val txs = transactionRepository.getByPortfolio(portfolioId)
        val signature = txs.hashCode()

        mutex.withLock { cache[portfolioId] }
            ?.takeIf { it.signature == signature }
            ?.let { return it.positions }

        // Stamp the portfolio's market onto every position so the market-data layer can namespace
        // bars. Looked up only on a cache miss; cached positions are already stamped.
        val market = portfolioRepository.getById(portfolioId)?.market ?: Market.US_STOCKS
        val positions = compute(txs).map { it.copy(market = market) }
        // Don't clobber a fresher entry written by a concurrent caller while we computed.
        mutex.withLock {
            val current = cache[portfolioId]
            if (current == null || current.signature == signature) {
                cache[portfolioId] = Entry(signature, positions)
            }
        }
        return positions
    }
}
