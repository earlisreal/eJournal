package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.InsertOrEnrichResult

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import io.earlisreal.ejournal.domain.analytics.marketClock

class SqlDelightTransactionRepository(private val db: AppDatabase) : TransactionRepository {

    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> =
        db.tradeTransactionQueries.selectByPortfolio(portfolioId).executeAsList().map { it.toDomain() }

    override suspend fun getByPortfolioAndDateRange(
        portfolioId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<Transaction> =
        db.tradeTransactionQueries
            .selectByPortfolioAndDateRange(portfolioId, from, to)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun insert(transaction: Transaction): Long? = insertRow(transaction)

    private fun insertRow(transaction: Transaction): Long? =
        // changes() and last_insert_rowid() are connection-local, and JdbcSqliteDriver pools
        // connections for file-backed DBs — so the INSERT and these reads must run on the SAME
        // connection or changes() sees 0 and a freshly-inserted row is wrongly reported as a skip.
        // A transaction pins all three to one connection.
        db.tradeTransactionQueries.transactionWithResult {
            db.tradeTransactionQueries.insertTransaction(
                portfolioId = transaction.portfolioId,
                symbol      = transaction.symbol,
                datetime    = transaction.datetime,
                action      = transaction.action,
                price       = transaction.price,
                shares      = transaction.shares,
                fees        = transaction.fees,
                externalId  = transaction.externalId,
                verifiedExecutionInstant = transaction.verifiedExecutionInstant,
                verifiedMarketDate = transaction.verifiedMarketDate
            )
            // INSERT OR IGNORE skips duplicate externalIds; changes() is 0 when nothing was inserted,
            // in which case lastInsertRowId() would return a stale id — so report the skip as null.
            if (db.tradeTransactionQueries.changes().executeAsOne() == 0L) null
            else db.tradeTransactionQueries.lastInsertRowId().executeAsOne()
        }

    override suspend fun insertOrEnrich(transaction: Transaction): InsertOrEnrichResult =
        db.tradeTransactionQueries.transactionWithResult {
            val market = db.portfolioQueries.selectById(transaction.portfolioId).executeAsOne().market
            val existing = transaction.externalId?.let {
                db.tradeTransactionQueries.selectByIdentity(transaction.portfolioId, it).executeAsOneOrNull()?.toDomain()
            }
            val instant = transaction.verifiedExecutionInstant
            val date = transaction.verifiedMarketDate
            val invalid = instant != null && date != null && instant.toLocalDateTime(marketClock(market)).date != date
            if (existing == null) {
                // Invalid proof never discards a valid financial row.
                insertRow(transaction.copy(verifiedExecutionInstant = if (invalid) null else instant, verifiedMarketDate = if (invalid) null else date))
                if (invalid) InsertOrEnrichResult.INSERTED_WITH_CONFLICT else InsertOrEnrichResult.INSERTED
            } else {
                val mergedInstant = existing.verifiedExecutionInstant ?: instant
                val mergedDate = existing.verifiedMarketDate ?: date
                val conflict = invalid || existing.datetime != transaction.datetime || existing.symbol != transaction.symbol ||
                    existing.action != transaction.action || existing.price != transaction.price || existing.shares != transaction.shares ||
                    (existing.verifiedExecutionInstant != null && instant != null && existing.verifiedExecutionInstant != instant) ||
                    (existing.verifiedMarketDate != null && date != null && existing.verifiedMarketDate != date) ||
                    (mergedInstant != null && mergedDate != null && mergedInstant.toLocalDateTime(marketClock(market)).date != mergedDate)
                when {
                    conflict -> InsertOrEnrichResult.CONFLICTING
                    mergedInstant == existing.verifiedExecutionInstant && mergedDate == existing.verifiedMarketDate -> InsertOrEnrichResult.UNCHANGED
                    else -> {
                        db.tradeTransactionQueries.updateTiming(mergedInstant, mergedDate, existing.id)
                        InsertOrEnrichResult.TIMING_ENRICHED
                    }
                }
            }
        }

    override suspend fun replaceFeesByExternalId(portfolioId: Long, feesByExternalId: Map<String, Double>) {
        db.tradeTransactionQueries.transaction {
            feesByExternalId.forEach { (externalId, fees) ->
                require(fees.isFinite()) { "Transaction fee must be finite" }
                db.tradeTransactionQueries.updateFeesByPortfolioAndExternalId(
                    fees = fees,
                    portfolioId = portfolioId,
                    externalId = externalId,
                )
            }
        }
    }

    override suspend fun delete(id: Long) {
        // Also drop any tag assignments anchored to this transaction as an opener. Deleting a
        // non-opener fill matches no PositionTag/PositionNote rows, so this is a no-op in that case.
        db.tradeTransactionQueries.transaction {
            db.positionTagQueries.deleteAssignmentsForOpeningTx(id)
            db.positionNoteQueries.deleteNote(id)
            db.tradeTransactionQueries.deleteById(id)
        }
    }

    override suspend fun countByPortfolio(portfolioId: Long): Long =
        db.tradeTransactionQueries.countByPortfolio(portfolioId).executeAsOne()

    override suspend fun deleteByPortfolio(portfolioId: Long) {
        // Clear tag assignments for this portfolio's positions before the transactions vanish (the
        // cleanup query resolves openers via TradeTransaction, so it must run first).
        db.tradeTransactionQueries.transaction {
            db.positionTagQueries.deleteAssignmentsForPortfolio(portfolioId)
            db.positionNoteQueries.deleteNotesForPortfolio(portfolioId)
            db.tradeTransactionQueries.deleteByPortfolio(portfolioId)
        }
    }

    private fun io.earlisreal.ejournal.TradeTransaction.toDomain() = Transaction(
        id          = id,
        portfolioId = portfolioId,
        symbol      = symbol,
        datetime    = datetime,
        action      = action,
        price       = price,
        shares      = shares,
        fees        = fees,
        externalId  = externalId,
        verifiedExecutionInstant = verifiedExecutionInstant,
        verifiedMarketDate = verifiedMarketDate,
    )
}
