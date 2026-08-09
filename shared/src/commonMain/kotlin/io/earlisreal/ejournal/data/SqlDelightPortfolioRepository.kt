package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Broker
import kotlin.uuid.Uuid

class SqlDelightPortfolioRepository(private val db: AppDatabase) : PortfolioRepository {

    override suspend fun getAll(): List<Portfolio> =
        db.portfolioQueries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): Portfolio? =
        db.portfolioQueries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun insert(name: String, market: Market, broker: Broker?): Portfolio {
        val credentialRef = Uuid.random().toString()
        db.portfolioQueries.insertPortfolio(name, market, broker, credentialRef)
        return db.portfolioQueries.selectByCredentialRef(credentialRef).executeAsOne().toDomain()
    }

    override suspend fun update(id: Long, name: String, market: Market, broker: Broker?) {
        db.portfolioQueries.updatePortfolio(name, market, broker, id)
    }

    override suspend fun delete(id: Long) {
        db.portfolioQueries.deleteById(id)
    }

    private fun io.earlisreal.ejournal.Portfolio.toDomain() = Portfolio(id, name, market, broker, credentialRef)
}
