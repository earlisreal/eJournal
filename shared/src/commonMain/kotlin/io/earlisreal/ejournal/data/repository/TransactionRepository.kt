package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

interface TransactionRepository {
    suspend fun getByPortfolio(portfolioId: Long): List<Transaction>
    suspend fun getByPortfolioAndDateRange(
        portfolioId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<Transaction>
    /** Inserts a transaction, returning its new row id, or null if it was skipped as a duplicate (same externalId). */
    suspend fun insert(transaction: Transaction): Long?
    suspend fun delete(id: Long)
    suspend fun countByPortfolio(portfolioId: Long): Long
    suspend fun deleteByPortfolio(portfolioId: Long)
}
