package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio

interface PortfolioRepository {
    suspend fun getAll(): List<Portfolio>
    suspend fun getById(id: Long): Portfolio?
    suspend fun insert(name: String, market: Market): Long
    suspend fun update(id: Long, name: String, market: Market)
    suspend fun delete(id: Long)
}
