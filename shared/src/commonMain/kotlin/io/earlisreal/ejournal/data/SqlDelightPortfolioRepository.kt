package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Broker
import kotlin.uuid.Uuid

class SqlDelightPortfolioRepository(private val db: AppDatabase) : PortfolioRepository {

    private companion object {
        const val ALPACA_ENVIRONMENT_SETTING = "portfolio.alpacaEnvironment"
    }

    override suspend fun getAll(): List<Portfolio> =
        db.portfolioQueries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): Portfolio? =
        db.portfolioQueries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun insert(
        name: String,
        market: Market,
        broker: Broker?,
        alpacaEnvironment: AlpacaEnvironment?,
    ): Portfolio {
        val credentialRef = Uuid.random().toString()
        db.portfolioQueries.transaction {
            db.portfolioQueries.insertPortfolio(name, market, broker, credentialRef)
            val id = db.portfolioQueries.selectByCredentialRef(credentialRef).executeAsOne().id
            writeAlpacaEnvironment(id, alpacaEnvironment)
        }
        return db.portfolioQueries.selectByCredentialRef(credentialRef).executeAsOne().toDomain()
    }

    override suspend fun update(
        id: Long,
        name: String,
        market: Market,
        broker: Broker?,
        alpacaEnvironment: AlpacaEnvironment?,
    ) {
        db.portfolioQueries.transaction {
            db.portfolioQueries.updatePortfolio(name, market, broker, id)
            writeAlpacaEnvironment(id, alpacaEnvironment)
        }
    }

    override suspend fun delete(id: Long) {
        db.portfolioQueries.transaction {
            db.portfolioSettingQueries.deleteByPortfolio(id)
            db.portfolioQueries.deleteById(id)
        }
    }

    private fun writeAlpacaEnvironment(id: Long, environment: AlpacaEnvironment?) {
        if (environment == null) {
            db.portfolioSettingQueries.delete(id, ALPACA_ENVIRONMENT_SETTING)
        } else {
            db.portfolioSettingQueries.upsert(id, ALPACA_ENVIRONMENT_SETTING, environment.name)
        }
    }

    private fun io.earlisreal.ejournal.Portfolio.toDomain(): Portfolio {
        val environment = db.portfolioSettingQueries
            .get(id, ALPACA_ENVIRONMENT_SETTING)
            .executeAsOneOrNull()
            ?.let { value -> runCatching { AlpacaEnvironment.valueOf(value) }.getOrNull() }
        return Portfolio(
            id = id,
            name = name,
            market = market,
            broker = broker,
            credentialRef = credentialRef,
            alpacaEnvironment = environment,
        )
    }
}
