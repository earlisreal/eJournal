package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository

class SqlDelightPortfolioSettingsRepository(private val db: AppDatabase) : PortfolioSettingsRepository {

    override suspend fun getString(portfolioId: Long, key: String): String? =
        db.portfolioSettingQueries.get(portfolioId, key).executeAsOneOrNull()

    override suspend fun putString(portfolioId: Long, key: String, value: String) {
        db.portfolioSettingQueries.upsert(portfolioId, key, value)
    }

    override suspend fun getBoolean(portfolioId: Long, key: String, default: Boolean): Boolean =
        getString(portfolioId, key)?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(portfolioId: Long, key: String, value: Boolean) {
        putString(portfolioId, key, value.toString())
    }

    override suspend fun clear(portfolioId: Long) {
        db.portfolioSettingQueries.deleteByPortfolio(portfolioId)
    }
}
