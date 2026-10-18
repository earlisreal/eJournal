package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

enum class InsertOrEnrichResult { INSERTED, INSERTED_WITH_CONFLICT, TIMING_ENRICHED, UNCHANGED, CONFLICTING }

interface TransactionRepository {
    suspend fun insertOrEnrich(transaction: Transaction): InsertOrEnrichResult

    suspend fun getByPortfolio(portfolioId: Long): List<Transaction>
    suspend fun getByPortfolioAndDateRange(
        portfolioId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<Transaction>
    /** Inserts a transaction, returning its new row id, or null if it was skipped as a duplicate (same externalId). */
    suspend fun insert(transaction: Transaction): Long?

    /** Atomically replaces fees for the supplied broker transaction identities. */
    suspend fun replaceFeesByExternalId(portfolioId: Long, feesByExternalId: Map<String, Double>) {
        error("This transaction repository does not support atomic fee replacement")
    }

    suspend fun delete(id: Long)
    suspend fun countByPortfolio(portfolioId: Long): Long
    suspend fun deleteByPortfolio(portfolioId: Long)
}

data class IngestionCounts(val inserted: Int, val timingEnriched: Int, val conflicts: Int)

suspend fun TransactionRepository.insertOrEnrichAll(transactions: List<Transaction>): IngestionCounts {
    var inserted = 0
    var enriched = 0
    var conflicts = 0
    for (transaction in transactions) when (insertOrEnrich(transaction)) {
        InsertOrEnrichResult.INSERTED -> inserted++
        InsertOrEnrichResult.INSERTED_WITH_CONFLICT -> { inserted++; conflicts++ }
        InsertOrEnrichResult.TIMING_ENRICHED -> enriched++
        InsertOrEnrichResult.CONFLICTING -> conflicts++
        InsertOrEnrichResult.UNCHANGED -> Unit
    }
    return IngestionCounts(inserted, enriched, conflicts)
}
