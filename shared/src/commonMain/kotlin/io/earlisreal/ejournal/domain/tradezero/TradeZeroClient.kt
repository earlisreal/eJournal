package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDate

sealed class TradeZeroFetchResult {
    data class Success(val transactions: List<Transaction>) : TradeZeroFetchResult()
    data object InvalidCredentials : TradeZeroFetchResult()
    data class NetworkError(val message: String) : TradeZeroFetchResult()
}

interface TradeZeroClient {
    suspend fun testConnection(): ConnectionResult
    suspend fun fetchOrders(portfolioId: Long, from: LocalDate, to: LocalDate): TradeZeroFetchResult
}
