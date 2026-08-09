package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.time.Instant

data class AlpacaAccount(
    val id: String,
    val accountNumber: String?,
    val status: String?,
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
        val skippedOptions: Int = 0,
        val skippedCrypto: Int = 0,
    ) : AlpacaFetchResult

    data object InvalidCredentials : AlpacaFetchResult
    data class NetworkError(val message: String) : AlpacaFetchResult
}

interface AlpacaBrokerClient {
    suspend fun testConnection(): AlpacaConnectionResult

    suspend fun fetchFills(
        portfolioId: Long,
        after: Instant?,
        until: Instant?,
    ): AlpacaFetchResult
}
