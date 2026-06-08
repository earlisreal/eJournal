package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Portfolio

interface PortfolioRepository {
    suspend fun getAll(): List<Portfolio>
    suspend fun getById(id: Long): Portfolio?
    suspend fun insert(name: String, currency: String): Long
    suspend fun delete(id: Long)
}
