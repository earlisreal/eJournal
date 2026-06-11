package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio

class SqlDelightPortfolioRepository(private val db: AppDatabase) : PortfolioRepository {

    override suspend fun getAll(): List<Portfolio> =
        db.portfolioQueries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): Portfolio? =
        db.portfolioQueries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun insert(name: String, market: Market): Long {
        db.portfolioQueries.insertPortfolio(name, market)
        return db.portfolioQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun update(id: Long, name: String, market: Market) {
        db.portfolioQueries.updatePortfolio(name, market, id)
    }

    override suspend fun delete(id: Long) {
        db.portfolioQueries.deleteById(id)
    }

    private fun io.earlisreal.ejournal.Portfolio.toDomain() = Portfolio(id, name, market)
}
