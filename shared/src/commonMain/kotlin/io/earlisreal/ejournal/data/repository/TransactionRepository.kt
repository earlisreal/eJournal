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
    suspend fun insert(transaction: Transaction): Long
    suspend fun delete(id: Long)
}
