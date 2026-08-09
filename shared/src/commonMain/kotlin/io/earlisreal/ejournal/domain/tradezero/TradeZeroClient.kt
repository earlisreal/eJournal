package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDate

data class TradeZeroAccount(val id: String)

sealed interface TradeZeroConnectionResult {
    data class Connected(val account: TradeZeroAccount) : TradeZeroConnectionResult
    data object InvalidCredentials : TradeZeroConnectionResult
    data class NetworkError(val message: String) : TradeZeroConnectionResult
}

sealed interface TradeZeroFetchResult {
    data class Success(
        val transactions: List<Transaction>,
        val account: TradeZeroAccount,
    ) : TradeZeroFetchResult
    data object InvalidCredentials : TradeZeroFetchResult
    data class NetworkError(val message: String) : TradeZeroFetchResult
}

interface TradeZeroClient {
    suspend fun testConnection(credentials: TradeZeroBrokerCredentials): TradeZeroConnectionResult
    suspend fun fetchOrders(
        credentials: TradeZeroBrokerCredentials,
        portfolioId: Long,
        from: LocalDate,
        to: LocalDate,
    ): TradeZeroFetchResult
}
