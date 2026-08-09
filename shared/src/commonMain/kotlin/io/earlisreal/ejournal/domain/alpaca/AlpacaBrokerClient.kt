package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import kotlin.time.Instant

data class AlpacaAccount(
    val id: String,
    val accountNumber: String?,
    val status: String?,
)

data class AlpacaBrokerCredentials(
    val keyId: String,
    val secretKey: String,
    val environment: AlpacaEnvironment,
)

sealed interface AlpacaConnectionResult {
    data class Connected(
        val account: AlpacaAccount,
        val environment: AlpacaEnvironment,
    ) : AlpacaConnectionResult

    data object InvalidCredentials : AlpacaConnectionResult
    data class NetworkError(val message: String) : AlpacaConnectionResult
}

sealed interface AlpacaFetchResult {
    data class Success(
        val transactions: List<Transaction>,
        val account: AlpacaAccount,
        val detail: BrokerSyncDetail = BrokerSyncDetail(),
    ) : AlpacaFetchResult

    data object InvalidCredentials : AlpacaFetchResult
    data class NetworkError(val message: String) : AlpacaFetchResult
}

interface AlpacaBrokerClient {
    suspend fun testConnection(credentials: AlpacaBrokerCredentials): AlpacaConnectionResult

    suspend fun fetchFills(
        credentials: AlpacaBrokerCredentials,
        portfolioId: Long,
        after: Instant?,
        until: Instant?,
    ): AlpacaFetchResult
}
