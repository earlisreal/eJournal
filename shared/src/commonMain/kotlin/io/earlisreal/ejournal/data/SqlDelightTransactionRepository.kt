package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

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

    override suspend fun insert(transaction: Transaction): Long {
        db.tradeTransactionQueries.insertTransaction(
            portfolioId = transaction.portfolioId,
            symbol      = transaction.symbol,
            datetime    = transaction.datetime,
            action      = transaction.action,
            price       = transaction.price,
            shares      = transaction.shares,
            fees        = transaction.fees
        )
        return db.tradeTransactionQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun delete(id: Long) {
        db.tradeTransactionQueries.deleteById(id)
    }

    private fun io.earlisreal.ejournal.TradeTransaction.toDomain() = Transaction(
        id          = id,
        portfolioId = portfolioId,
        symbol      = symbol,
        datetime    = datetime,
        action      = action,
        price       = price,
        shares      = shares,
        fees        = fees
    )
}
