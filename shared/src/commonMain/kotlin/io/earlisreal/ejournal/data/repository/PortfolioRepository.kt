package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Broker

interface PortfolioRepository {
    suspend fun getAll(): List<Portfolio>
    suspend fun getById(id: Long): Portfolio?
    suspend fun insert(name: String, market: Market, broker: Broker? = null): Portfolio
    suspend fun update(id: Long, name: String, market: Market, broker: Broker? = null)
    suspend fun delete(id: Long)
}
